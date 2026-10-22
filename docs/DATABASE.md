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

**One row per product on the shelf (item rule, Nate, 2026-10-22).** If it is its own product in the store, it is its own
`inventory_item`: varieties and flavours included (Lavendelhonig ≠ Waldhonig; Chipotle-Senf ≠ Kräutersenf ≠ Dijon-Senf;
Erdbeer- ≠ Kirschjoghurt). Only the **brand** is abstracted away: Barilla spaghetti and any other spaghetti are one item,
"Spaghetti". Even a product only one company makes is its own item. A recipe or receipt line that names only the generic
product ("Honig", "Senf", "Joghurt") maps to the plain / standard item (1070 Honig, 1679 Senf, 386 Naturjoghurt);
`inventory_item_category` / `inventory_item_subcategory` group the varieties. New items come in a numbered migration
(`migrations/0009`, `0013`, `0014`, `0015`) with the same rows in `seed/inventory_catalog.sql`; the older generic items
that merged varieties under the previous rule are listed in `docs/variant_split_review.md`.

### `recipe`, `recipe_ingredient`, `recipe_instructions`

Read by `RecipeRepository`.

`recipe`: `recipe_id` (PK), `recipe_name`, `recipe_servings`, `recipe_cook_time`,
`recipe_prep_time`, and nullable `recipe_calories` / `recipe_protein` /
`recipe_carbs` / `recipe_fat` / `recipe_fibre` (`recipe_fiber` in the SQL —
watch the spelling) / `recipe_sugar` (nutrition is per serving), and nullable
`recipe_source_url` (the recipe page it was imported from, so refreshes match 1:1).

Servings ranges: `recipe_servings` is the number of people the stored ingredient amounts are
for, and the default for scaling. A recipe written for a range ("3 – 4 Personen") stores the
lower bound there (3) and the upper bound in nullable `recipe_servings_max` (4); "2 – 4" is 2 + 4.
`recipe_servings_max` is NULL for every recipe that is not a range — the normal (2-person) recipes
and explicit sizes like "für 4 Personen" (`recipe_servings` = 4). A CHECK
(`recipe_servings_max_above_servings`) keeps it NULL or strictly greater than `recipe_servings`.
Added by `migrations/0006_recipe_servings_max.sql`; the importer fills it (see `docs/BACKLOG.md`,
"Servings ranges"). The API scales from `recipe_servings` unless `?basis=max` is asked for; the
shopping list always scales from `recipe_servings`.

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

### `tag`, `recipe_tag`, `inventory_item_animal`, `recipe_unmatched_ingredient` — recipe tags and the diet filter

`tag`: `tag_id` (PK), `tag_name` (UNIQUE), `tag_category` (the group: Tier, Ernährung, Inhalt, Zeit, Nährwerte,
Küche for the derived tags). `recipe_tag`: `(recipe_tag_recipe_id, recipe_tag_id)` PK, FKs to `recipe` and
`tag`, `recipe_tag_source` `'manual'` (default; added by a person or the API) or `'derived'` (written by
`RecipeTagRunner`, the only rows it ever replaces), and an index `recipe_tag_by_tag (recipe_tag_id,
recipe_tag_recipe_id)` for "all recipes with tag X". `inventory_item_animal`: `(inventory_item_animal_item_id,
inventory_item_animal_animal)` PK, FK to `inventory_item`, CHECK animal in Hähnchen, Pute, Geflügel, Ente, Gans, Rind,
Schwein, Lamm, Kalb, Wild, Kaninchen, Pferd, Fisch, Meeresfrüchte; an item can have several;
`inventory_item_animal_certainty` `'certain'` (default; gives a Tier tag) or `'possible'` (a generic item such as
Salami or Gelatine that may be from this animal: no Tier tag, blocks vegetarisch, feeds "anpassbar").
`inventory_item.inventory_item_alcohol` (BOOLEAN, default FALSE): the item contains alcohol as an ingredient.
`recipe_unmatched_ingredient`: `recipe_unmatched_ingredient_id` (PK), `_recipe_id` (FK to `recipe`, indexed),
`_line_no` (position on the recipe page, NULL for an unchecked recipe), `_raw_text` (the page line, VARCHAR(500)),
`_reason` `'unmatched'` (a line with no catalogue item; `recipe_ingredient_item_id` is NOT NULL, so the importer used
to drop it) or `'unchecked'` (no URL / page; no line), `_created_at`. A recipe with ANY row here is "unklar". Added by
migrations 0011 and 0012; SQL in `TagRepository`, `ItemAnimalRepository`, `RecipeTagRepository` (package
`de.dahaem.tags`) and `DietFilterRepository` (package `de.dahaem.diet`).

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
- **`store_offer_price_change`** (migration `0007`) — one row per price change
  of an offer that is already stored. See below.

## Offer price-change history (`store_offer_price_change`)

`store_offer` is upserted on its key, so a re-pull in the same week overwrites
the prices. The refresh runs daily, so `StoreRepository.saveOffers` keeps the
trend: when a re-pulled offer's sale, normal or deal price differs from the
stored row, it first inserts one `store_offer_price_change` row (old and new of
all three prices, `observed_on` = that pull's `pulled_on`, `created_at`), then
updates the offer as before — both in the same transaction, so a failed save
leaves neither. Prices are compared as `DECIMAL(10,2)`, NULL = NULL counts as
unchanged. No row when nothing changed, and none for a new offer: the
`store_offer` row itself is the first observation. The full price history of
one offer is its first pulled prices (the oldest change's `old_*` values, or
the row itself when there are no changes) followed by each change in order:

```sql
SELECT c.store_offer_price_change_observed_on AS observed_on,
       c.store_offer_price_change_old_sale_price AS old_sale, c.store_offer_price_change_new_sale_price AS new_sale,
       c.store_offer_price_change_old_normal_price AS old_normal, c.store_offer_price_change_new_normal_price AS new_normal,
       c.store_offer_price_change_old_deal_price AS old_deal, c.store_offer_price_change_new_deal_price AS new_deal
FROM store_offer_price_change c
WHERE c.store_offer_price_change_offer_id = ?
ORDER BY c.store_offer_price_change_observed_on, c.store_offer_price_change_id;
```

See `StoreRepository.java`'s `SCHEMA` array for the exact DDL (types,
constraints, foreign keys) — it's the closest thing to a source of truth this
repo has for these six tables.

## Purchase receipts (`purchase_receipt`, `purchase_receipt_line`, `purchase_receipt_adjustment`)

Migration `0008`, written by `de.dahaem.receipts.ReceiptImportRunner` (PENNY receipts as
`pdftotext -layout` text). `purchase_history` does not fit a till receipt (it needs a `store_item`
and an expiration date, has an INTEGER quantity and no receipt link), and `PurchaseService` adds to
stock, which a historical import must never do — so receipts have their own tables and the import
writes neither `purchase_history` nor `inventory_item`.

- **`purchase_receipt`** — one row per receipt: store, `purchased_at`, `receipt_number` (4 digits,
  repeats over time), printed `total`, app / other savings, loyalty points, `imported_at`.
  `UNIQUE (store_id, purchased_at, receipt_number)` is the duplicate guard; a re-run reports the
  receipt as a duplicate and writes nothing.
- **`purchase_receipt_line`** — EVERY printed product line, matched or not: `raw_name` as printed,
  `brand` only from a known prefix (`LH` = Lindenhof; usually NULL), quantity (pieces or kg), unit /
  weight / price per kg, `line_total` (printed) and `paid_total` (after the discounts attached to the
  line), tax symbol and rate, the `*` discount-excluded marker, Pfand per unit, and the optional
  `inventory_item_id` with its `match_source` (`override` | `rule` | `manual`; NULL when unmatched).
- **`purchase_receipt_adjustment`** — discounts, app coupons, Treueartikel, Frischerabatt, Pfand and
  Leergut with their signed amount and printed text; `line_id` is the product they follow, NULL if none.
  Coupons ("App-Coupon …", "Ferrero Kinder 25%") are adjustments on the product line before them.

Per receipt: `SUM(paid_total)` + every adjustment that is not a discount already inside a line
(deposits, returns, unattached discounts) = `total`. The importer checks this before it writes and
skips (and reports) a receipt that does not balance.

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

- **No migration tool.** `migrations/` holds numbered `.sql` files; 0001 and 0002 were written for the
  old SQLite file and are kept for history, 0003 onwards are MariaDB. Each is applied by hand
  (snapshot first — see `CLAUDE.md`). Worth a lightweight tool (e.g. Flyway) before the schema
  changes much more.
