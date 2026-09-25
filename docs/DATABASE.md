# Database

DAHÄM uses one SQLite file (`data/kitchen_organizer.db` by default, overridable
via the `dahaem.db.path` system property — see `de.dahaem.shared.Database`). It
is gitignored and not distributed with the repo.

There is **no schema migration tool and no full `schema.sql`** in this repo.
`de.dahaem.stores.StoreRepository.ensureSchema()` creates the raw-layer store
tables and back-fills a few columns on the pre-existing verified-layer tables,
and is called automatically at the start of any `StoreImportRunner` job. Every
other table used by the app — `inventory_item`, `recipe`, `recipe_ingredient`,
`recipe_instructions`, `store`, `brand`, `store_item`, `store_discount` — is
assumed to already exist; nothing in the Java code creates them. **A genuinely
fresh checkout has no way to create a working database from this repo alone**
(see [Gaps](#gaps) below).

The column layout below is reverse-engineered from the SQL in the repositories
(`InventoryRepository`, `RecipeRepository`, `StoreRepository`,
`ShoppingListRepository`), not from a canonical schema file, since none exists.

## Tables the code assumes already exist

### `inventory_item` — the catalog (food + non-food)

Read by `InventoryRepository`, joined by `RecipeRepository` and `StoreRepository`.

| Column | Notes |
|---|---|
| `inventory_item_id` | PK |
| `inventory_item_name_english` | |
| `inventory_item_name_deutsch` | nullable |
| `inventory_item_quantity` | current pantry stock; written by `InventoryRepository.updateQuantity` |
| `inventory_item_unit` | |
| `inventory_item_category` | |
| `inventory_item_subcategory` | nullable |
| `inventory_item_earliest_exp_date` | stored/read as ISO text, nullable |

### `recipe`, `recipe_ingredient`, `recipe_instructions`

Read by `RecipeRepository`.

`recipe`: `recipe_id` (PK), `recipe_name`, `recipe_servings`, `recipe_cook_time`,
`recipe_prep_time`, and nullable `recipe_calories` / `recipe_protein` /
`recipe_carbs` / `recipe_fat` / `recipe_fibre` (`recipe_fiber` in the SQL —
watch the spelling) / `recipe_sugar`.

`recipe_ingredient`: `recipe_ingredient_item_id` (FK -> `inventory_item`),
`recipe_ingredient_recipe_id` (FK -> `recipe`), `recipe_ingredient_unit`,
`recipe_ingredient_quantity_needed`.

`recipe_instructions`: `recipe_instructions_step_id` (PK),
`recipe_instructions_recipe_id` (FK -> `recipe`),
`recipe_instructions_step_number`, `recipe_instructions_instruction`,
`recipe_instructions_notes` (nullable), `recipe_instructions_source`.

A `recipe_tag` table is mentioned in `RecipeRepository`'s class comment but no
query in the repo currently reads or writes it.

### `store`, `brand`

Referenced by `StoreRepository` and the store_item rebuild in
`makeStoreItemPriceNullable`. `store` has `store_id` (PK) and `store_name` (used
as the natural key in `findOrCreateStore`). `brand` has `brand_id` (PK);
no column list appears anywhere in this repo.

### `store_item`, `store_discount` — the verified layer

Pre-existing tables that `StoreRepository.ensureSchema()` extends rather than
creates. Known columns, from the `store_item_new` rebuild in
`makeStoreItemPriceNullable` and the `STORE_DISCOUNT_COLUMNS` back-fill:

`store_item`: `store_item_id` (PK), `store_item_brand_id` (FK -> `brand`),
`store_item_store_id` (FK -> `store`), `store_item_item_id` (FK ->
`inventory_item`), `store_item_package_unit`, `store_item_package_size`,
`store_item_price` (**nullable** — a flyer-bootstrapped item has no known normal
price, since the flyer price is the sale price on `store_discount`),
`store_item_is_available`, plus three columns this repo adds on top:
`store_item_deposit` (Pfand per unit), `store_item_store_product_id` (FK ->
`store_product`, ties a promoted item back to the exact raw product it came
from), `store_item_external_id` (stable per-chain id for a flyer-bootstrapped
item that has no `store_product`).

`store_discount`: base columns not listed in this repo (read via
`ShoppingListRepository`/`StoreDiscountService`), plus two columns this repo
adds: `store_discount_deal_quantity` (default `1`) and `store_discount_deal_price`
(multi-buy deal terms, e.g. "3 for €5").

`store_offer` gets one back-filled column beyond its own `CREATE TABLE` below:
`store_offer_source` defaults to `'STORE'` so rows pulled before the
provenance column existed default to the direct-store source.

## Tables this repo creates (`StoreRepository.SCHEMA`) — the raw layer

All created with `CREATE TABLE IF NOT EXISTS` by `ensureSchema()`.

- **`store_import_run`** — one row per importer run: store, kind (`RANGE` /
  `OFFERS`), started/finished timestamps, status (`RUNNING` / `OK` / `PARTIAL` /
  `FAILED`), pages crawled, item count, free-text notes.
- **`store_product`** — the raw, as-pulled product range for a store. Unique on
  `(store_product_store_id, store_product_external_id)`. Carries name, brand,
  sales unit, regular/base price, deposit, category, description, url,
  availability, first/last-seen dates, and an optional
  `store_product_inventory_item_id` set once `StoreMatchService` matches it.
- **`store_product_variant`** — flavour/size variants of a `store_product`.
- **`store_price_history`** — one row per recorded price point for a
  `store_product`.
- **`store_catalog_change`** — a diff row (`NEW` / `PRICE` / `RETURNED` /
  `REMOVED`, old/new price) produced by comparing a range pull against the
  previously known range, tied to the `store_import_run` that detected it.
- **`store_offer`** — a raw, as-pulled weekly flyer/promo listing. Unique on
  `(store_offer_store_id, store_offer_external_id, store_offer_valid_from,
  store_offer_region)`. Carries sale price, optional multi-buy deal quantity/
  price, normal/base price, deposit, promo text, category, region, validity
  window, pulled-on date, and `store_offer_source` (`STORE` / `AGGREGATOR` /
  `RECEIPT`, in trust order receipt > direct store > aggregator).

See `StoreRepository.java`'s `SCHEMA` array for the exact DDL (types,
constraints, foreign keys) — it's the closest thing to a source of truth this
repo has for these six tables.

## Gaps

- **No way to bootstrap a fresh database.** `inventory_item`, `recipe`,
  `recipe_ingredient`, `recipe_instructions`, `store`, `brand`, `store_item`, and
  `store_discount` are never created by any code in this repo. A new contributor
  following the README's "Getting started" section (`mvn package`, run `Main`)
  will hit a `SQLException` on the very first query unless they already have a
  database file with these tables from elsewhere. Consider committing a
  `schema.sql` (or a repository-level `ensureSchema()` for these tables, the way
  `StoreRepository` already does for its own) plus `scripts/recipes/samples/`
  loaded against it, so `mvn package` really is enough to get started.
- **No migration story.** `ensureSchema()`'s `CREATE TABLE IF NOT EXISTS` +
  manual `addMissingColumns`/rebuild-table helpers work, but there's no version
  tracking — if a column needs to change type or a table needs a real migration
  beyond "add column" or "make nullable," each case needs its own hand-written
  one-off method (as `makeStoreItemPriceNullable` already is). Worth a lighter
  migration tool (even just numbered `.sql` files applied in order) before the
  raw-layer schema grows much further.
