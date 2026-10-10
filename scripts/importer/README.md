# Recipe importer and quantity repair

Python tools that turn HelloFresh recipe pages into DAHÄM recipes in MariaDB, and the
repair tool that fixes the amounts of recipes imported before the parser fixes below.

| File | What it does | Touches the DB |
|------|--------------|----------------|
| `recipe_parse.py` | parsing only: amounts, units, "nach Geschmack", servings, titles | no |
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
  applied.
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

The recipe table had no URL before, so recipes are matched by name:

1. The URL slug must equal the recipe name, or start with it. Umlaut spelling is ignored.
2. The page's JSON-LD name, cut the way the importer cut titles, must equal the stored name.
3. A recipe that already has `recipe_source_url` uses that page.

The summary counts matched, ambiguous and unmatched recipes:

- **Ambiguous**: several pages with this name that disagree on their ingredients.
- **Unmatched**: no slug or no confirmed page.

Neither is changed.

### What goes into the SQL

| Class | Example |
|-------|---------|
| ½-bug | 5 Stück -> 0.5 Stück |
| rounding | 2 -> 1.5, 1 -> 0.25 |
| unit changed | 2 Stück -> 2 Prise, 3 Stück -> 3 EL |
| now to-taste | 1 Stück Salz -> to taste |
| inserted EL/TL lines | lines the old parser dropped |
| inserted to-taste lines | lines the old parser dropped |
| bundle servings | 2 -> 4 |
| `recipe_source_url` | set where empty |

These are listed in the TSV but **not** written to the SQL:

- the page now simply says something else, e.g. 250 g -> 300 g (`--include-other` writes them);
- new lines that are neither spoons nor to-taste;
- lines whose item match is uncertain;
- stored rows that are not on the page.
