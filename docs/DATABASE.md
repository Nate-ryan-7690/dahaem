# Database

DAHÄM uses **MariaDB** (11.4) as its single source of truth. The Java backend connects
through a HikariCP pool configured entirely from the environment — see
`de.dahaem.shared.Database`:

| Variable | Example |
|---|---|
| `DAHAEM_DB_URL` | `jdbc:mariadb://db:3306/<dbname>` |
| `DAHAEM_DB_USER` | the application user |
| `DAHAEM_DB_PASSWORD` | its password — never committed |

`schema.sql` is the MariaDB DDL (24 tables, InnoDB, foreign keys enforced). It is the
canonical schema; nothing in the Java code creates or alters tables any more.

The column layout below describes the tables the repositories read and write
(`InventoryRepository`, `RecipeRepository`, `StoreRepository`, `ShoppingListRepository`, ...).

## Identity tables (`household`, `app_user`, `household_member`)

Created by `schema.sql`, or by `migrations/0002_add_household_and_app_user.sql`
on an older database. All SQL for them is in `de.dahaem.auth.AccountRepository`.

| Table | Columns | Notes |
|-------|---------|-------|
| `household` | `household_id` PK, `household_name`, `created_date` | one row today (single shared household) |
| `app_user` | `user_id` PK, `user_email` UNIQUE, `user_display_name`, `user_password_hash`, `user_created_date` | email stored trimmed + lower-cased; the hash is BCrypt, never plaintext. Not named `user` (reserved in some engines) |
| `household_member` | `household_member_id` PK, `household_id` → household, `user_id` → app_user, `role` | `role` is `ADMIN` or `MEMBER`; `UNIQUE(household_id, user_id)` |

No other table references `household` yet: scoping inventory, recipes, meal
plans, shopping lists and purchases per household is a later migration.

## Core tables

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
| `inventory_item_earliest_exp_date` | `DATE`, nullable (bound/read as `LocalDate`) |

### `recipe`, `recipe_ingredient`, `recipe_instructions`

Read by `RecipeRepository`.

`recipe`: `recipe_id` (PK), `recipe_name`, `recipe_servings`, `recipe_cook_time`,
`recipe_prep_time`, and nullable `recipe_calories` / `recipe_protein` /
`recipe_carbs` / `recipe_fat` / `recipe_fibre` (`recipe_fiber` in the SQL —
watch the spelling) / `recipe_sugar` (nutrition is per serving), and nullable
`recipe_source_url` (the recipe page it was imported from, so refreshes match 1:1).

`recipe_ingredient`: `recipe_ingredient_item_id` (FK -> `inventory_item`),
`recipe_ingredient_recipe_id` (FK -> `recipe`), `recipe_ingredient_unit`,
`recipe_ingredient_quantity_needed` (`DECIMAL(10,3)`, nullable: the exact amount for
`recipe_servings` people, e.g. 0.5 Stück, 2 EL) and `recipe_ingredient_to_taste`
(`BOOLEAN`, default FALSE). A CHECK keeps a row to exactly one of: a quantity, or
`to_taste = TRUE` with quantity NULL and an empty unit ("Salz nach Geschmack": the recipe
needs the item, no amount). To-taste rows never reach a shopping list. Changed by
`migrations/0005_recipe_quantities.sql`; how the importer fills them is in
`scripts/importer/README.md`.

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

## Store raw layer (`store_product`, `store_offer`, ...)

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

## Dates and times

`DATE` columns are bound and read as `java.time.LocalDate`; `store_import_run`'s
`DATETIME` columns as `java.time.LocalDateTime` (`setObject` / `getObject(col, Class)`),
not as ISO strings. A `LocalDateTime.toString()` value (ISO `T` separator, up to nine
fractional digits) is not a valid `DATETIME` literal.

## Loading the schema and seeds

Only ever load these into a **new, empty** database — never over the live one.
Throwaway database example:

```bash
mariadb -u root -p -e "CREATE DATABASE dahaem_scratch CHARACTER SET utf8mb4"
mariadb -u root -p dahaem_scratch < schema.sql
mariadb -u root -p dahaem_scratch < seed/inventory_catalog.sql
mariadb -u root -p dahaem_scratch < scripts/recipes/samples/sample_recipes.sql
mariadb -u root -p dahaem_scratch < seed/demo_data.sql      # fabricated demo rows
```

The seed files use `INSERT IGNORE`, so re-running them against a scratch database
is harmless. The test suite does the same thing automatically against a Testcontainers
MariaDB.

## Gaps

- **No migration tool.** `migrations/` holds numbered `.sql` files that were written for the old
  SQLite file and are kept for history; MariaDB changes need to be written and applied by hand
  (snapshot first — see `CLAUDE.md`). Worth a lightweight tool (e.g. Flyway) before the schema
  changes much more.
