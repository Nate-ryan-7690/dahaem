# Architecture

This document expands on the [README](../README.md)'s architecture summary with
per-package responsibilities, the store data pipeline, and the rules new code is
expected to follow. It reflects the codebase as of this writing, including parts
that are stubs or in progress.

## Layering rule

Every feature package under `src/main/java/de/dahaem/` follows the same three-layer
split:

| Layer        | Contains                                   | Must not contain           |
|--------------|---------------------------------------------|-----------------------------|
| `model`      | Plain data (Java records)                    | Logic, SQL                  |
| `repository` | All SQL for that feature, parameterized      | Business logic               |
| `service`    | Business logic only                          | SQL, UI/console code         |

Console runners (`Main`, `ShoppingListDemo`, `StoreImportRunner`) sit outside this
split on purpose: they are temporary entry points that exercise the services until
a web layer replaces them, and are expected to disappear without the service layer
changing. New code should call into a `*Service`, not open a `Connection` or issue
SQL directly, and should not put lookup/aggregation/matching rules inside a
repository method.

`de.dahaem.shared.Database` is the single place that knows how to open a JDBC
connection (`jdbc:sqlite:<path>`, from the `dahaem.db.path` system property or
`data/kitchen_organizer.db` by default) and turns on `PRAGMA foreign_keys = ON`
per-connection, since SQLite does not enforce foreign keys otherwise.

## Package map

```
src/main/java/de/dahaem/
  shared/       Database — the one place that opens a JDBC connection
  auth/         logins + households (AppUser, Household, AccountRepository, AccountService, UserAdminRunner)
  api/          thin Spring controllers + SecurityConfig (session login, CSRF) over the services
  inventory/    catalog + pantry (Item, InventoryRepository, InventoryService)
  mealplanner/  recipes, ingredients, instructions (Recipe*, RecipeRepository, RecipeService)
  stores/       importers + raw/verified store data model (see below)
  shopping/     shopping list assembly (in progress)
  finance/      purchases (planned — currently empty stub classes only)
```

- **`auth`** — the identity model: `household`, `app_user` and
  `household_member`. `AccountService` creates accounts (the first one creates
  the household as `ADMIN`, later ones join as `MEMBER`) and only ever hands
  BCrypt hashes to the repository. `UserAdminRunner` is the operator's CLI for
  it; there is no public sign-up. Spring Security itself is configured in
  `api/SecurityConfig`, which looks users up through `AccountService`, so the
  auth package stays free of web code. Existing data is not household-scoped yet.
- **`inventory`** — read/update access to `inventory_item`: the generic
  household-item catalog (food and non-food) that recipes, shopping lists and
  store products all reference by id.
- **`mealplanner`** — recipes and their catalog-linked ingredients and
  step-by-step instructions.
- **`stores`** — by far the largest package (~2,800 lines across 26 files). It
  owns the web importers, the raw/verified store data model, and the matching and
  promotion logic that links pulled store data back to the inventory catalog. See
  [Store data pipeline](#store-data-pipeline) below.
- **`shopping`** — aggregates ingredient quantities across a set of recipes and
  picks the cheapest current store option per ingredient. Marked "in progress" in
  the README; `ShoppingListDemo` is its console proof-of-concept.
- **`finance`** — `Purchase`, `PurchaseRepository`, `PurchaseService` are all
  empty classes today. This package is a placeholder for the planned
  purchase-history feature, not yet wired to anything.

## Store data pipeline

The `stores` package is a two-layer pipeline, matching the README's diagram:

```
store sites --importers--> RAW              --match/promote-->  VERIFIED   --> shopping list
                           store_product                        store_item
                           store_offer                          store_discount
```

**Raw layer** (`StoreRepository`, `AldiNordClient`, `PennyClient`,
`AggregatorClient`, `HttpFetcher`): everything pulled from a store site or the
aktionspreis.de aggregator, stored as-is. `store_product` is the full product
range (ALDI only, today); `store_offer` is a weekly flyer/promo listing, tagged
with its `store_offer_source` (`STORE` for a direct chain pull, `AGGREGATOR` for
aktionspreis.de — direct always outranks aggregator when both exist for the same
product). Every pull also writes a `store_import_run` row (status, timing, item
count) and, for range pulls, `store_catalog_change` rows (`NEW` / `PRICE` /
`RETURNED` / `REMOVED`) computed by comparing against the previously known range
in `StoreImportService.importAldiRange`.

**Verified layer** (existing `store_item` / `store_discount` tables, extended by
`StoreRepository.ensureSchema()` with a few store-specific columns): only
complete, catalog-matched products become `store_item` rows, and matched flyer
offers become `store_discount` rows on those items. This is the layer the
shopping list actually reads.

**Matching and promotion**, the step in between:

- `StoreMatchService` links `store_product` rows to `inventory_item` catalog rows
  by name (exact match, then longest-whole-word-contains match, with manual
  overrides from `config/store_overrides.json` taking priority). It matches
  conservatively on purpose — a store sells specific branded products, the
  catalog holds generic ingredients, and inventing catalog rows for a ~1,700-item
  ALDI range (much of it non-food) would wreck the curated catalog. Unmatched
  products are written to `data/stores/unmatched_*.tsv` for a human to review,
  never guessed into the database.
- `StorePromotionService` promotes matched, available, priced, parseable-pack-size
  `store_product` rows into verified `store_item` rows.
- `StoreDiscountService` turns matched flyer offers into `store_item` (bootstrapped
  if needed, since offer-only chains like Penny and every aggregator chain have no
  range/`store_product` import) + `store_discount` rows. This step has to run last
  in both scheduled refreshes (see `docs/refresh.md`) because it links against
  whatever `store_item`/catalog state exists at the time it runs.

`StoreImportRunner` (a CLI entry point, not a service) wires these together per
job (`range aldi`, `offers all`, `match aldi`, `promote`, `discounts`) and exposes
two orchestrator jobs, `refresh weekly` and `refresh monthly`, that chain the
steps in dependency order for a scheduler. Each step runs independently — one
chain's failure doesn't stop the others — and the process exits `0` only if every
step in the job succeeded. Full detail, including the scheduling rationale and
sample systemd units, is in [`docs/refresh.md`](refresh.md).

## Database schema

There is no schema migration tool or `schema.sql` in this repo. `StoreRepository`
owns `ensureSchema()`, which creates the raw-layer tables (`store_product`,
`store_offer`, `store_import_run`, `store_catalog_change`,
`store_product_variant`, `store_price_history`) with `CREATE TABLE IF NOT EXISTS`
and back-fills a handful of new columns on the pre-existing `store_item` /
`store_discount` / `store_offer` tables. It's called once at the top of
`StoreImportRunner.main`, so any store-importer run brings a fresh database up to
date for that part of the schema.

Nothing in the repo creates `inventory_item`, `recipe`, `recipe_ingredient`,
`recipe_instructions`, `store`, `brand`, `store_item`, or `store_discount` — the
repositories that read/write them (`InventoryRepository`, `RecipeRepository`,
`StoreRepository`) all assume those tables already exist. See
[`docs/DATABASE.md`](DATABASE.md) for the column layout inferred from the SQL in
this repo, and its note on what a from-scratch setup is currently missing.

## Testing

Tests live under `src/test/java/de/dahaem/`, mirroring the main package layout,
and run against a real (temporary) SQLite file rather than mocks — see
`Database`'s `dahaem.db.path` system property. Today only `shopping` (1 test
class) and `stores` (6 test classes, covering the pure-logic pieces:
`Normalizer`, `VariantParser`, `JsonUtil`, `AggregatorClient`, `StoreMatchService`,
`StorePromotionService`) have coverage. `inventory`, `mealplanner`, `finance` and
`shared` have none. CI (`.github/workflows/ci.yml`) runs `mvn verify` on every
push/PR to `main`.

## Data-prep scripts (`scripts/`)

Not part of the Java application; run by hand, not on a schedule (see
`docs/refresh.md` for why):

- `scripts/importer/*.py` — recipe PDF extraction and matching (`extract.py`,
  `rematch.py`, `load_recipes.py`), catalog maintenance (`add_ingredients.py`,
  `dedup_catalog.py`, `scan_ingredients.py`), and store-page discovery
  (`discover.py`).
- `scripts/recipes/samples/sample_recipes.sql` — the 15 original sample recipes
  shipped with the repo.
- `config/store_overrides.json`, `config/stores.properties.example` — importer
  configuration; the real `stores.properties` is gitignored (personal store id /
  postal code).
