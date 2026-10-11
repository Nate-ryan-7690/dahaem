# Recipe importer and quantity repair

Python tools that turn HelloFresh recipe pages into DAHÄM recipes in MariaDB, and the
repair tool that fixes the amounts of recipes imported before the parser fixes below.

| File | What it does | Touches the DB |
|------|--------------|----------------|
| `recipe_parse.py` | parsing only: amounts, units, "nach Geschmack", servings, titles | no |
| `step_amounts.py` | amounts written in the cooking steps (`1 EL [1,5 EL \| 2 EL] Öl*`) for ingredient lines without a number | no |
| `discover.py` | sitemap -> `recipe_urls.txt` (one request) | no |
| `extract.py` | recipe pages -> reviewable draft `.sql` in `scripts/recipes/_drafts/` | reads the catalog |
| `load_recipes.py` / `load.py` | drafts -> MariaDB (`load.py` refuses drafts with REVIEW markers) | writes only with `--commit` |
| `rematch.py` | re-match draft REVIEW lines against the current catalog | reads the catalog |
| `repair_quantities.py` | pages -> guarded repair SQL + review TSV for recipes already in the DB | reads only, never writes |
| `db.py` | the MariaDB connection, from environment variables | |

`add_ingredients.py` and `dedup_catalog.py` are older catalog tools that still use the
SQLite file from before the MariaDB move. They are not part of the import pipeline.

## Setup

```bash
pip install -r scripts/importer/requirements.txt     # PyMySQL
export DAHAEM_DB_URL='jdbc:mariadb://db:3306/dahaem'  # same variables as the Java API
export DAHAEM_DB_USER=...
export DAHAEM_DB_PASSWORD=...                         # never on the command line, never committed
```

Tests (no database, no network: saved JSON-LD fixtures):

```bash
python3 -m unittest discover -s scripts/importer/tests
```

## How amounts are stored

`recipe_ingredient_quantity_needed` is `DECIMAL(10,3)`; `recipe_ingredient_to_taste` marks an
ingredient the recipe needs without an amount (`migrations/0005_recipe_quantities.sql`).

| Page line | quantity | unit | to_taste |
|-----------|----------|------|----------|
| `½ Stück Zitrone`, `1/2 Stück`, `0,5 Stück` | 0.5 | Stück | 0 |
| `1½`, `1 ½`, `1 1/2`, `1.5` | 1.5 | | 0 |
| `⅓` / `⅔` | 0.333 / 0.667 | | 0 |
| `3 Esslöffel Olivenöl` / `3 EL Olivenöl` | 3 | EL | 0 |
| `2 Teelöffel Zucker` | 2 | TL | 0 |
| `2 Prisen Zucker` | 2 | Prise | 0 |
| `nach Geschmack Salz`, `Salz nach Geschmack`, `etwas Salz`, `Salz` | NULL | (empty) | 1 |

- Amounts are never rounded and never forced to at least 1.
- Spoons stay spoons; nothing is converted to ml.
- `kg` and `l` are kept as written; the importer never converted them.
- A range such as `1-2 Stück` keeps the first value.
- To-taste rows never add a quantity to a shopping list. Restocking salt and pepper is the
  inventory's job (low-stock), not the recipes'.
- **Servings** come from `recipeYield`. When the full page title marks a bundle for 4 (`4P`,
  `Bundle 4P`, `für 4 Personen`), servings are 4, because those recipes carry 4-person amounts
  but say `recipeYield: 2`. Any other disagreement between title and yield is reported, not
  applied. Without a readable `recipeYield` (and no bundle title) a new import gets the default
  2; the repair tool never writes that default over a stored value.
  - **Ranges** are kept as ranges: `für 3 – 4 Personen` gives servings 3 (the lower bound, which
    scaling uses by default) and `recipe_servings_max` 4 (`migrations/0006_recipe_servings_max.sql`);
    `2 – 4 Personen` gives 2 and 4. Only a range written with `Personen` counts.
  - A 4-person title that also says another number of `Portionen` ("OTP 4P … 2 Portionen")
    contradicts itself: `review-servings`, nothing written.
  - Titles that only say `N Portionen` (ready-made products), or `für 6 Personen`, are reported
    and stay 2.
- **Lines without a number** (Nate, 2026-10-11), in this order:
  1. `nach Geschmack X` is to taste, whatever X is (also when a step names an amount);
  2. otherwise the amount comes from the **cooking steps**: the amount outside the bracket is the
     2-person value, several mentions of the same ingredient are added up. Only a mention
     worded like the line counts (`(Oliven-)Öl` = `Öl` = `Olivenöl`); a different wording
     (`Essig` for `Weißweinessig`, `Senf` for `mittelscharfer Senf`, `Zitronensaft` for
     `Zitrone`) or mentions in different units (1 EL + 1 TL) go to review, never guessed;
  3. no amount anywhere: salt, pepper, oil and vinegar are to taste; `Stück X` is 1 Stück
     (pita, pomegranate), except for uncountable things (sugar, butter, honey, flour, milk,
     stock …), which go to review; everything else goes to review (`review-amount`);
  4. a line whose kind of ingredient already has a numbered line in the recipe (`1 Esslöffel
     Olivenöl` next to `Stück Öl`) gets no row of its own.
- **Two lines on the same catalog item** (the primary key is item + recipe):
  - same unit: the quantities are summed;
  - a line with an amount beats a to-taste line;
  - different units: the first line is kept and the conflict is reported.

## Importing new recipes

```bash
python extract.py --list recipe_urls.txt --resume            # drafts + _report.txt, reads the catalog
python rematch.py                                            # optional: re-match REVIEW lines
python load_recipes.py                                       # DRY RUN: what would load
python load_recipes.py --commit                              # writes; one transaction per recipe
```

New drafts record the page in `recipe.recipe_source_url`.

## Repairing recipes already in the database

`repair_quantities.py` re-reads the pages of recipes that were imported with the old parser.
It writes three files to `scripts/importer/_repair/` and **never changes the database**:

- `repair_<selector>_<stamp>.sql`: guarded statements with no BEGIN/COMMIT. Each statement
  only takes effect while the row is unchanged:
  - `UPDATE … WHERE` quantity, unit and to_taste still equal what the tool read;
  - `INSERT … WHERE NOT EXISTS`;
  - servings only where they still equal the old value;
  - the source URL only where it is still empty.
- `repair_<selector>_<stamp>.tsv`: one line per update or insert, plus every held line and every
  unmatched or ambiguous recipe. Open it in a spreadsheet to review.
- `repair_<selector>_<stamp>.txt`: the summary.

### Options

| Option | Meaning |
|--------|---------|
| `--select suspect` | recipes with a non-g/ml row of quantity 5, 25, 75, 333 or 667 (the ½/¼/¾/⅓/⅔ bug) |
| `--select all` | every recipe |
| `--urls FILE` | URL lists. Default: `recipe_urls.txt` + `recipe_urls_tail.txt` here (git-ignored, supplied by Nate) |
| `--delay 2` | seconds between requests. Each request uses a normal browser user agent and `robots.txt` is honoured |
| `--cache DIR` | page cache, default `_cache/pages/`. **This makes runs resumable**: re-running the same command skips every cached page, including 404s. Network errors are not cached and are retried |
| `--offline` | cache only, no network (e.g. to regenerate the files after changing the parser) |
| `--include-other` | also write changes that are not one of the known bugs (see below) |
| `--limit N` | only the first N selected recipes (trial runs) |

### Matching recipes to pages

The recipe table had no URL before, so recipes are matched **by name, never by URL slug**:

1. Every page of the URL lists is fetched (or read from the cache) and indexed by its JSON-LD name.
2. Page name and stored name are both cut the way the importer cut titles (subtitle after a double
   space, marketing blurb, 120 characters) and compared ignoring case, umlaut spelling and
   punctuation.
3. A recipe that already has `recipe_source_url` uses that page (the name must still agree).

The summary counts matched, ambiguous and unmatched recipes:

- **Ambiguous**: several pages have this name (even with identical lines), or several stored
  recipes share the name so a page cannot be assigned to one of them. Nothing is written.
- **Unmatched**: no page has this name. When some pages could not be read yet, the summary says
  "incomplete, re-run to retry".

Why not slugs: they are often just the first half of the title ("…-tacos" for "… Tacos mit selbst
gemachter Salsa und Mayonnaise"), spelled differently ("&" is "and", "Harissabaguette" is
"harissabagutte") or come from an older title. In a sample of 20 unmatched recipes, 14 had a page
with exactly the stored name behind a shorter slug.

Because every page has to be known, the selector (`suspect` / `all`) is applied to the database
rows after matching, and **both selectors need the full fetch once**; the cache serves the second.
If two lines of a page land on the same catalog item they are only added up when both matches are
confident; an uncertain line is listed on its own (`review-uncertain-match`) and never summed.

### Overnight run

```bash
# from the PHP project folder; credentials come from the containers' environment
docker run --rm --network php-sandbox_default --user 1000:1000 --env-file <env file with DAHAEM_DB_*> \
  -e HOME=/tmp -e PYTHONUSERBASE=/tmp/pyl -v /home/nate/dahaem/scripts/importer:/work -w /work python:3.12-slim \
  sh -c 'pip install -q --user -r requirements.txt && python repair_quantities.py --select suspect --delay 2'
```

1. The first run fetches all ~12,162 pages (about 7 hours at 2 s) into `_cache/pages/`; the run
   can be stopped and started again, cached pages and 404s are skipped.
2. It then writes the `suspect` files. Review the TSV and apply the SQL as described below.
3. `--select all` reads the same cache (no new requests) and writes its own set of files. Review
   and apply them separately, after the `suspect` repair.

### What goes into the SQL

| Class | Example |
|-------|---------|
| ½-bug | 5 Stück -> 0.5 Stück |
| rounding | 2 -> 1.5, 1 -> 0.25 |
| unit changed | 2 Stück -> 2 Prise, 3 Stück -> 3 EL |
| now to-taste | 1 Stück Salz -> to taste |
| inserted EL/TL lines | lines the old parser dropped |
| inserted to-taste lines | lines the old parser dropped |
| servings | 2 -> 4 (explicit 4-person title), 2 -> 3 + maximum 4 (range) |
| from-steps / inserted-from-steps lines | amountless line, amount read from the cooking steps |
| inserted 1 Stück lines | `Stück X` without an amount anywhere |
| `recipe_source_url` | set where empty |

An update or insert is only written when the page line matches its item **confidently**
(override, rule or exact). A unit change is only written when it is a **known equivalence**:
the stored row is exactly what the old parser made of this very page line. The old parser knew
fewer unit words, so "3 Esslöffel Olivenöl" was stored as 3 Stück and "2 Prisen Zucker" as
2 Stück. The new unit then fixes a parser bug and is not a guess. If the stored row is
something the old parser would *not* have made of the line, it came from somewhere else, often
a wrong item match.

These are listed in the TSV but **not** written to the SQL:

| TSV action | Why it is held |
|------------|----------------|
| `review-uncertain-match` | any change (update or insert) where the item match is only `like`/`multi`, e.g. "1 Packung Bratapfelgewürz" fuzzily matched to Apfel. The TSV shows old and new values, the page line and the match |
| `review-unit` | the unit differs and the old parser would have stored something else for this line (e.g. stored 1 Stück, but the old parser would have made "1 TL Honig" into 1 TL) |
| `review-servings` | the page has no readable `recipeYield` and no title rule, or the title contradicts itself (4P + "2 Portionen"); the stored servings are kept |
| `review-amount` | an amountless line with no safe amount: steps worded differently or in different units, uncountable `Stück X`, no amount anywhere, or covered by a numbered line |
| `review-held` | the page now simply says something else (250 g -> 300 g), or a new line that is neither a spoon nor to-taste. `--include-other` writes these |
| `review-unmatched` | the page line matches no catalog item |
| `db-only` | a stored row that is not on the page; left as is |
| `ambiguous` / `unmatched` | the recipe itself could not be matched to exactly one page |

### Reading the review TSV before applying the SQL

1. Sort or filter by `action`. `update`, `insert` and `update-servings` rows are exactly what the
   SQL does.
2. Spot-check `update` rows per `class`: `half-bug` (old 5/25/75/333/667 -> new below 1),
   `rounding`, `unit`, `to-taste`. Compare `page_line` with old and new.
3. Read every `review-*` row. They change nothing. Fix them by hand later (or improve
   `overrides.json` and re-run with `--offline`) if they matter.
4. Only then apply the SQL in one transaction.
