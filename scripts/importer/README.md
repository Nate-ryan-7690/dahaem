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
| `find_unmatched_ingredients.py` | cached pages -> the recipe lines that have no catalogue item (guarded SQL for `recipe_unmatched_ingredient` + report) | reads only, never writes |
| `rematch_unmatched.py` | the stored unmatched lines re-sorted against the current catalogue: guarded SQL (inserts + deletes), review TSV, no-item list, report | reads only, never writes |
| `non_food.json` | config: recipe lines that are not food (Holzspieße, Backpapier ...) | |
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
  - The **title** decides when it names people (all scraped recipes say `recipeYield: 2`). Only a
    number next to `Personen`, `Person`, `Pers.`, `Portionen` or `Portion`, or `4P` / `Bundle 4P` /
    `OTP 4P`, counts; a bare number pair elsewhere in a title never does.
    - one number N: servings N (`4 Portionen` 4, `für 6 Personen` 6, `1 Pers.` 1; 2 = unchanged);
    - a range `N – M` / `N-M` next to Personen or Portionen: servings N (the lower bound, used for
      scaling by default) and `recipe_servings_max` M (migration 0006, CHECK max > servings);
    - two different numbers, or a number outside the range (`OTP 4P … 2 Portionen`,
      `1 Portion für 2 Prs. oder 2 Portionen für 1 Prs.`): `review-servings`, nothing written;
    - no head count in the title: 2.
- **Lines without a number** (Nate, 2026-10-11), in this order:
  1. `nach Geschmack X` is to taste, whatever X is (also when a step names an amount);
  2. otherwise the amount comes from the **cooking steps**: the amount outside the bracket is the
     2-person value, several mentions of the same ingredient are added up. A mention counts when it
     is worded like the line (`(Oliven-)Öl` = `Öl` = `Olivenöl`). For oil, vinegar and mustard a
     generic mention (`Essig`, `Senf`, `Öl`) also counts when the recipe has exactly ONE item of that
     kind (the only vinegar); with several it goes to review. `Zitronensaft` is not `Zitrone` and
     `Limettenspalten` are not `Limette`: review. A line whose ingredient already has a numbered line
     in the recipe gets no row of its own;
  3. EL and TL of the same ingredient are converted exactly (1 EL = 3 TL) and stored in EL with
     3 decimals; spoons together with g, ml or Prise go to review;
  4. no amount anywhere: salt, pepper, oil, vinegar and the uncountable staples sugar, butter,
     stock / fond, honey, flour, mustard and milk are to taste; `Stück X` of a countable thing (egg,
     lemon, lime, garlic clove, pomegranate, pita, Schmand, spring onion, onion, carrot, cucumber,
     orange, pepper, tomato, chilli, beetroot, bread rolls) is 1 Stück; anything else is
     `review-amount` and never in the SQL.
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

A page line the importer cannot match to a catalogue item gets no `recipe_ingredient` row (the item id is NOT NULL),
but it is no longer forgotten: `extract.py` writes it into the draft as `-- UNMATCHED <line no> <page line>`, and
`load_recipes.py` stores it in `recipe_unmatched_ingredient` (`'unmatched'`) in the same transaction as the recipe.
Older drafts only have the ingredient name in the REVIEW comment of their id-0 row; that name is stored without a
line number. A `--commit` run stops when migration 0012 is not applied. A recipe with such a row is "unklar" for the
diet filter and never gets vegetarisch / vegan.

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
| servings | from the title: 2 -> N, or 2 -> N + maximum M for a range |
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
| `review-amount` | an amountless line with no safe amount: steps worded differently or in units that cannot be added, several candidates for a generic mention, an uncountable-or-unknown `Stück X`, no amount anywhere, or covered by a numbered line |
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

## Recipe enrichment (`enrich_recipes.py`)

Fills the recipe columns of `migrations/0010_recipe_enrichment.sql` (description, image links, thumbnail,
image source, cuisine, rating, publish date) from the JSON-LD that `repair_quantities.py` already cached
in `_cache/pages/`. It never writes: it reads that cache and MariaDB (read-only transaction, `db.py`) and
writes `_enrich/enrich_<stamp>.sql` (git-ignored) and a report `.txt`. Cache pages are matched to recipes
by `recipe_source_url` only.

- Images are **linked** (URLs only; the browser loads them from HelloFresh) and `recipe_image_source` is
  `hellofresh`, so they can be replaced in bulk later (`own`, `generated`). The two URLs and the source are
  one group: set together, only while all three are NULL.
- Cuisine goes through `config/cuisine_map.json` (raw value -> one German name, `0` / empty -> none). A raw
  value that is not in the map stays NULL and is counted in the report: add it to the map and re-run.
- One `UPDATE` per recipe, each field set only while it is NULL (`COALESCE`), `WHERE recipe_id = N AND` one of
  its fields is still NULL: a re-run changes nothing, a value set later is never overwritten. No
  `BEGIN` / `COMMIT`: wrap the file in one transaction.

```bash
python enrich_recipes.py            # dry: writes the files only, nothing touches the database
# review _enrich/enrich_<stamp>.txt, then apply (after a snapshot and migration 0010):
{ echo 'START TRANSACTION;'; cat _enrich/enrich_<stamp>.sql; echo 'COMMIT;'; } | \
  docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"'
```

Check the row counts against the report's "recipes with an UPDATE" before the COMMIT.

## Unmatched recipe lines (`find_unmatched_ingredients.py`)

`recipe_ingredient.recipe_ingredient_item_id` is NOT NULL, so until now the importer **dropped every ingredient line it
could not match to a catalogue item**. A recipe whose "Speck" line was dropped looks complete and could be tagged
vegetarisch. This tool finds those lines for the recipes already in the database; going forward `load_recipes.py`
stores them itself (above). It never writes: it reads the page cache of `repair_quantities.py` (`_cache/pages/`) and
MariaDB (read-only transaction, `db.py`) and writes `_unmatched/unmatched_<stamp>.sql`, a `.tsv` with every line and a
report `.txt` (all git-ignored; they hold real recipe lines).

For every recipe with a `recipe_source_url` the cached `recipeIngredient` lines are parsed with `recipe_parse` and
matched exactly as the importer and the repair tool do (the catalogue from the database plus `overrides.json`, water
skipped). A line whose item is not among the recipe's stored ingredient items is `unmatched` (the raw line is stored,
with its position on the page). A recipe without a URL, without a cached page, or whose page has no recipe gets ONE
`unchecked` row without a line. The report counts the recipes checked, with unmatched lines and unchecked, and lists
the 60 most common unmatched names with their line and recipe counts: map the top ones (overrides or catalogue items)
so the recipes stop being unklar (see `docs/BACKLOG.md`, "Unmatched recipe ingredients").

The SQL has one `INSERT` per row, each skipped when that row already exists (and when the recipe no longer exists), so
it can be applied twice. No `BEGIN` / `COMMIT`: wrap the file in one transaction, after a snapshot and migration 0012.

```bash
python find_unmatched_ingredients.py     # writes the files only, nothing touches the database
# review _unmatched/unmatched_<stamp>.txt, then apply (after a snapshot and migration 0012):
{ echo 'SET NAMES utf8mb4; START TRANSACTION;'; cat _unmatched/unmatched_<stamp>.sql; echo 'COMMIT;'; } | \
  docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"'
```

Then derive the tags again (`RecipeTagRunner`): every recipe with a row becomes unklar and loses a vegetarisch /
vegan tag it may have had.

## Re-sorting the unmatched lines (`rematch_unmatched.py`)

Most stored unmatched lines are pantry staples (Öl, Honig, Essig, Zucker, Mehl, Salz ...) that DO have a catalogue item
today: older imports dropped them, so those recipes also miss them in the shopping list. This tool reads the stored
`recipe_unmatched_ingredient` rows of reason `unmatched`, re-matches each against the CURRENT catalogue and
`overrides.json` (the importer's matching, confident matches only) and sorts every one into exactly one case
(decided by Nate, 2026-10-14):

| Case | What happens |
|------|--------------|
| a) matches an item now | **covered** when the recipe already has the item or an item of the same family: `Öl` / `Olivenöl` / `(Oliven-)Öl*` with an olive oil (or `Öl`) the recipe has; the Essig family as `step_amounts.py` defines it (the same wording, or one side is the bare `Essig`; two specific vinegars differ); `Salz` / `nach Geschmack Salz` and `Pfeffer` (the bare word) with any salt / pepper the recipe has. No insert, the unmatched row is resolved. **Otherwise INSERT** a `recipe_ingredient` row with quantity / unit / to-taste exactly as the importer parses the line (to taste for salt, pepper, oil, vinegar ... without an amount; amounts from the cooking steps via `step_amounts.py`, like the repair tool) and resolve the row. Two lines for the same new item in one recipe: same unit are summed, an amount beats a to-taste line, **different units go to REVIEW** and nothing is inserted |
| b) non-food | `non_food.json` (Holzspieße, Backpapier, Alufolie ...): resolved as ignored, the recipe is not unklar because of it. `Schaschlikspieße` only when a cooking step puts food on them (else it might be the prepared meat skewers: REVIEW) |
| c) no item yet | stays unmatched, the recipe stays unklar; the name goes to `_noitem.tsv` with line / recipe counts (catalogue batch 2) |
| d) ambiguous | **REVIEW TSV**, nothing changed: an uncertain item match (`like`, `like-multi`, `exact-multi`), an amountless line the steps cannot settle, a line no longer on the cached page, a page missing from the cache, different units for one item |

It only READS (MariaDB read-only transaction via `db.py`, and the page cache `_cache/pages/`; `unchecked` rows are never
touched) and writes to `_rematch/` (git-ignored: real recipe lines): `rematch_<stamp>.sql`, `.tsv` (every row with its
case), `_review.tsv` (case d only), `_noitem.tsv` and the report `.txt` (counts per case, rows to insert / delete,
recipes that stop being unklar, the 60 most common names that stay).

The SQL has no `BEGIN` / `COMMIT`. Every `INSERT` is skipped when the recipe or the item is missing or a
`(recipe, item)` row exists; every `DELETE` is by id and only fires while the row is still what the tool read and, for
case a, while the recipe now has the item that resolves it. It can be applied twice. Last statement: a `SELECT` that
must say 0 on the first run.

```bash
python rematch_unmatched.py              # writes the files only, nothing touches the database
# read _rematch/rematch_<stamp>.txt and _review.tsv, then (after a snapshot, migrations 0012 + 0013 and the rebuild):
{ echo 'SET NAMES utf8mb4; START TRANSACTION;'; cat _rematch/rematch_<stamp>.sql; echo 'COMMIT;'; } | \
  docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"'
```

Compare the number of `INSERT` and `DELETE` statements in the file with the report before the `COMMIT`. Then derive the
tags again (`RecipeTagRunner`): recipes without a row left may become vegetarisch / vegan again.
