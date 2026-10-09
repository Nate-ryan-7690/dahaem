# Scheduled data refresh

`StoreImportRunner` has two orchestrator jobs that chain the existing store-import
steps into the order they actually depend on each other, so a scheduler can run
one command instead of a human running each step by hand:

```
java --enable-native-access=ALL-UNNAMED -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.stores.StoreImportRunner refresh weekly
java --enable-native-access=ALL-UNNAMED -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.stores.StoreImportRunner refresh monthly
```

Both are pure orchestration: every step calls the same `StoreImportService` /
`StoreMatchService` / `StorePromotionService` / `StoreDiscountService` methods the
individual CLI jobs already use (`offers aldi`, `offers penny`, `offers aggregator`,
`range aldi`, `match aldi`, `promote`, `discounts`). No importer logic changed.

Run either from the app directory (the one containing `data/` and `config/`), same
as any other `StoreImportRunner` job.

## In-app scheduler (alternative to the CLI jobs)

The same two flows can run inside the always-on `dahaem-api` container, so no systemd
timer or host cron is needed. Both the CLI (`StoreImportRunner refresh ...`) and the
scheduler call `RefreshService.refreshWeekly()` / `refreshMonthly()`, so they run
identical steps in the same order.

**It is opt-in and OFF by default** (the importers scrape external sites, personal use
only; dev, tests and CI must never trigger them). Enable it in the `dahaem-api`
environment:

```
REFRESH_ENABLED=true            # or the property dahaem.refresh.enabled=true
```

| Env var | Property | Default |
|---|---|---|
| `REFRESH_ENABLED` | `dahaem.refresh.enabled` | `false` |
| `REFRESH_WEEKLY_CRON` | `dahaem.refresh.weekly-cron` | `0 0 6 * * MON` (Mondays 06:00) |
| `REFRESH_MONTHLY_CRON` | `dahaem.refresh.monthly-cron` | `0 0 5 1 * *` (1st of month 05:00) |
| `REFRESH_ZONE` | `dahaem.refresh.zone` | `Europe/Berlin` |

Cron format is Spring's six fields: `second minute hour day-of-month month day-of-week`.

Behaviour: weekly and monthly share one overlap guard (a run that starts while another
is in progress is logged and skipped, not queued); scheduled methods never throw, so one
failed run does not stop later ones; start, each step's result and the finish are logged.
Use either the scheduler or the CLI timers, not both, or the flows run twice.

## `refresh weekly`

```
offers aldi + offers penny (offers all)  ->  offers aggregator all  ->  discounts
```

1. **Offers all** – pulls this week's (+ next week's preview) flyer offers directly
   from ALDI Nord and Penny. These are the two chains with a clean direct feed.
2. **Offers aggregator all** – pulls the same week's offers for the chains with no
   direct feed (Netto, Edeka, Rewe, Lidl, famila) plus a cross-check pull for ALDI,
   from the aktionspreis.de aggregator (`AggregatorClient.STORES`, tagged
   `SRC_AGGREGATOR` downstream, ranked below a direct pull).
3. **Discounts** – turns every matched flyer offer pulled in steps 1–2 into a
   `store_item` + `store_discount` row. This has to run *last*: it links offers
   against whatever `store_item`/catalog state exists at the time it runs, so it
   needs the freshest offers already in the database, and it also bootstraps the
   `store_item` shells for the offer-only chains (Penny + every aggregator chain
   have no `range`/`store_product` import to create those rows any other way).

Each step is independent: a failed aggregator store or a failed ALDI/Penny pull
doesn't stop the others from running, and `discounts` still runs over whatever
offers are pulled. The process exits `0` only if every step succeeded; `1` if any
step failed or came back partial.

Intended cadence: weekly, whenever fresh flyer offers are published.

## `refresh monthly`

```
range aldi  ->  match aldi  ->  promote  ->  discounts
```

1. **Range aldi** – pulls the ALDI Nord full product range (first run = initial
   load; later runs log NEW/PRICE/RETURNED/REMOVED changes into
   `store_catalog_change`).
2. **Match aldi** – links the `store_product` rows the range pull just
   wrote/refreshed to `inventory_item` catalog rows by name; unmatched rows go to
   `data/stores/unmatched_aldi.tsv` for review. This needs the range pull to have
   just run so it's matching against current stock, not a stale range.
3. **Promote** – promotes matched-and-complete `store_product` rows (matched,
   available, priced, parseable pack size) into the verified `store_item` layer
   the shopping list reads. This needs step 2's matches to exist first.
4. **Discounts** – same step as in the weekly pipeline, run again here so that any
   offers pulled since the last weekly refresh get re-linked against the
   `store_item` rows `promote` just (re)created/refreshed in step 3. Also last for
   the same reason as above: it must see the freshest `store_item` state.

Same independent-step behavior and exit codes as `refresh weekly`.

Intended cadence: monthly (ALDI's full range doesn't change often enough to justify
a weekly full-range crawl).

## What's on-demand, NOT part of the scheduled refresh

These touch curated/reviewed content and are run by hand when there's new material
to import, not on a timer:

### Recipe import (HelloFresh PDFs -> `recipe` / `recipe_ingredient`)

```
python scripts/importer/extract.py                 # PDF -> draft .sql, no DB writes
python scripts/importer/rematch.py --write          # re-match drafts against current catalog
python scripts/importer/load_recipes.py --reload --commit   # load reviewed drafts into the DB
```

- `extract.py` turns downloaded HelloFresh PDFs into reviewable draft `.sql` files
  under `scripts/recipes/_drafts/`; nothing is written to the DB.
- `rematch.py --write` re-runs the ingredient matcher over those drafts against the
  *current* catalog/overrides (the catalog grows over time, so old drafts can pick
  up newer matches) without re-fetching from HelloFresh.
- `load_recipes.py --reload --commit` loads the reviewed drafts into
  `data/kitchen_organizer.db`, skipping duplicate recipe names and dropping
  unmatched ingredient lines (logged in the draft comments for follow-up).

This isn't scheduled because each step expects a human to review the drafts
in between (dry-run first, `--commit`/`--write` only once satisfied) — there's no
"latest PDFs" feed to poll.

### Catalog additions (`inventory_item`)

```
python scripts/importer/add_ingredients.py --file ingredients_to_add.tsv --commit
```

Inserts curated `inventory_item` rows from a hand-curated TSV (english/deutsch/
category/subcategory). This is only run when someone has actually curated a batch
of missing ingredients (e.g. from `ingredients_missing.tsv`) — there's nothing to
schedule.

## Sample systemd units (Ubuntu server)

Adjust `WorkingDirectory` and the jar path to match the deployment. `Type=oneshot`
timers exit after the job finishes; systemd handles the schedule and simple retry
semantics.

`/etc/systemd/system/dahaem-refresh-weekly.service`
```ini
[Unit]
Description=DAHÄM store refresh (weekly): offers + discounts
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=dahaem
WorkingDirectory=/opt/dahaem
ExecStart=/usr/bin/java --enable-native-access=ALL-UNNAMED -cp /opt/dahaem/target/dahaem-1.0-SNAPSHOT.jar de.dahaem.stores.StoreImportRunner refresh weekly
```

`/etc/systemd/system/dahaem-refresh-weekly.timer`
```ini
[Unit]
Description=Run dahaem-refresh-weekly.service every week

[Timer]
OnCalendar=Mon *-*-* 04:00:00
Persistent=true

[Install]
WantedBy=timers.target
```

`/etc/systemd/system/dahaem-refresh-monthly.service`
```ini
[Unit]
Description=DAHÄM store refresh (monthly): full range + match + promote + discounts
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=dahaem
WorkingDirectory=/opt/dahaem
ExecStart=/usr/bin/java --enable-native-access=ALL-UNNAMED -cp /opt/dahaem/target/dahaem-1.0-SNAPSHOT.jar de.dahaem.stores.StoreImportRunner refresh monthly
```

`/etc/systemd/system/dahaem-refresh-monthly.timer`
```ini
[Unit]
Description=Run dahaem-refresh-monthly.service every month

[Timer]
OnCalendar=*-*-01 03:00:00
Persistent=true

[Install]
WantedBy=timers.target
```

Enable both timers with:

```
sudo systemctl daemon-reload
sudo systemctl enable --now dahaem-refresh-weekly.timer dahaem-refresh-monthly.timer
```

Check exit status / last run with `systemctl status dahaem-refresh-weekly.service`
and `journalctl -u dahaem-refresh-weekly.service`; a non-zero exit (`1`) means at
least one step failed or was partial — check the printed per-step summary for
which one.
