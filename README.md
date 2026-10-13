# DAHÄM

[![CI](https://github.com/Nate-ryan-7690/dahaem/actions/workflows/ci.yml/badge.svg)](https://github.com/Nate-ryan-7690/dahaem/actions/workflows/ci.yml)

A self-hosted household organizer that keeps inventory, meal plans, shopping lists, store prices and discounts, and purchases for a two-person household in one Java application on one SQLite database.

DAHÄM is Kurpfälzisch (the Mannheim-area dialect) for *daheim*, "at home." The name says where the software belongs rather than what any single feature does.

> Status: work in progress. This is an apprenticeship final-project built to production practices (clear layering, one deployable jar, importers meant to run on a schedule), not a shipped product with users.

```
                              DAHÄM
                                |
        +-----------------------+-----------------------+
        |                       |                       |
      Essen                 Haushalt                 Planung
   Rezepte, Angebote     Vorräte, Verbrauch     Einkauf, Finanzen
        |                       |                       |
        +-----------------------+-----------------------+
                                |
                   inventory_item catalog (food + non-food)
```

## What it does

- **Inventory catalog + pantry.** One catalog of generic household items, food and non-food, that recipes, shopping lists, and store products all reference.
- **Meal planning.** Recipes with catalog-linked ingredients and step-by-step instructions. 15 original sample recipes ship in the repo, built with deliberate ingredient overlap to demo shopping-list aggregation.
- **Store price and discount tracking.** Importers pull weekly flyer offers, and for ALDI the full online range. A two-layer model separates raw pulls from a verified layer.
- **Shopping list** (in progress). Assembled from recipes and low-stock items, matched against current store discounts.
- **Purchases and finance** (planned). Purchase history as ground-truth prices.

## Store importers

- **ALDI Nord.** Full range (catalogue) plus weekly offers, read from the site's `__NEXT_DATA__` JSON.
- **Penny.** Weekly offers from the public `/.rest` JSON API, per store price region.
- **aktionspreis.de aggregator.** Weekly offers for five chains without a clean direct feed (Lidl, Edeka, Rewe, famila, Hol-Ab; famila resolved through a postal-code session), plus ALDI as a cross-check.

Importers are plain Java (`java.net.http`), meant to run on the server on a schedule. Every offer carries its provenance (direct store, aggregator, or receipt), with a trust order of receipt over direct store over aggregator.

## Architecture

Java 17, Maven, MariaDB (`mariadb-java-client` + HikariCP), Jackson for JSON, Spring Boot (web + security) for the REST API and its login. `mvn package` builds one runnable jar with the maven-shade plugin, so the server needs only Java and that jar. Shade rather than Spring Boot's repackaged jar keeps the jar flat, so `java -cp` can still start the importers while `java -jar` starts the API.

**MariaDB** is the shared store: the Java engine owns the business logic behind the REST API, a separate PHP front end presents it, and both talk to the same database. Connection settings come from `DAHAEM_DB_URL`, `DAHAEM_DB_USER` and `DAHAEM_DB_PASSWORD`; nothing secret lives in the repo.

**Package-by-feature**, with a strict per-feature layer rule:

- `model` holds plain data (Java records), no logic.
- `repository` holds all SQL, parameterized.
- `service` holds logic only, no SQL and no UI.

The web layer added later calls the services without touching SQL.

**Two-layer store data.** The raw layer is everything pulled, as pulled. Only complete, catalog-matched products become verified `store_item` rows (the layer the shopping list reads); flyer offers become `store_discount` rows on those items.

```
store sites --importers--> RAW              --match-->  VERIFIED        --> shopping list
                           store_product                store_item
                           store_offer                  store_discount
```

Each verified item links back to its source product, so duplicate or stale store listings are resolved by recency and availability, not by lowest price (a stale relisted item can be cheaper than the one actually on the shelf).

## Repository layout

```
src/main/java/de/dahaem/
  shared/       Database: the single JDBC connection point
  inventory/    catalog + pantry
  mealplanner/  recipes, ingredients, instructions
  stores/       importers + raw/verified store model
  shopping/     shopping list (in progress)
  finance/      purchases (planned)
scripts/importer/          Python data-prep tools (recipe import, quantity repair, catalog build; see its README)
scripts/recipes/samples/   15 original sample recipes (SQL)
config/                    importer settings templates
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the layering rule and the
store data pipeline in detail, [`docs/DATABASE.md`](docs/DATABASE.md) for the
schema (and its current gaps), [`docs/refresh.md`](docs/refresh.md) for the
scheduled import jobs, [`CONTRIBUTING.md`](CONTRIBUTING.md) for setup and
conventions, and [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md) for a point-in-time
review of the codebase's strengths and known issues.

## Getting started

Requires Java 17+, Maven, Docker (the tests start a throwaway MariaDB with Testcontainers) and a MariaDB to run against.

```bash
mvn package
export DAHAEM_DB_URL=jdbc:mariadb://localhost:3306/dahaem
export DAHAEM_DB_USER=...
export DAHAEM_DB_PASSWORD=...
```

`schema.sql` is the MariaDB schema; `seed/inventory_catalog.sql`, the sample recipes and `seed/demo_data.sql`
give a fresh database something to run against. Load them into a **new, empty** database as described in
[`docs/DATABASE.md`](docs/DATABASE.md#loading-the-schema-and-seeds) — never over a database that holds real
data. The store/brand/price/discount rows in `seed/demo_data.sql` are entirely fabricated for the demo —
see the note at the top of that file — not real pulled store data.

Run the console entry point from your IDE (`de.dahaem.Main`), or a store importer from the command line:

```bash
java --enable-native-access=ALL-UNNAMED -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.stores.StoreImportRunner offers all
```

PENNY receipts (`pdftotext -layout -enc UTF-8` output, one `.txt` per receipt) are imported as purchase
history with `ReceiptImportRunner` — a dry run by default, `--commit` writes all new receipts in one
transaction into the receipt tables (migration 0008). It never changes stock or `purchase_history`. Receipt
texts are personal data: keep them outside the repo.

```bash
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.receipts.ReceiptImportRunner /path/to/receipt-texts            # dry run
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.receipts.ReceiptImportRunner /path/to/receipt-texts --commit
```

### Recipe tags

Recipe tags come from DAHÄM's own data, in five groups (`tag_category`): **Tier** (Hähnchen, Pute, Rind,
Schwein, Lamm, Kalb, Wild, Fisch, Meeresfrüchte), **Ernährung** (vegetarisch, vegan), **Zeit** (schnell =
prep + cook ≤ 30 min), **Nährwerte** (kalorienarm ≤ 600 kcal, proteinreich ≥ 30 g, per serving; thresholds in
`config/recipe_tags.properties`) and **Küche** (the recipe's cuisine, once `recipe.cuisine` exists). Nothing in
the catalogue records which animal a meat item is, so that is its own table, `inventory_item_animal`
(migration 0011), filled in two steps with `ItemAnimalRunner` — everything the name does not say is
marked REVIEW and only stored after a person has looked at it:

```bash
# 1. propose animals for the meat / fish items (read-only) -> TSV: item id, name, category, subcategory, proposed animals, CONFIDENT / REVIEW
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.ItemAnimalRunner --out items.tsv
# 2. edit items.tsv: fix the animals of a REVIEW row (e.g. Schwein+Rind) and set its status to REVIEWED
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.ItemAnimalRunner --apply items.tsv            # dry run
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.ItemAnimalRunner --apply items.tsv --commit   # CONFIDENT + REVIEWED rows, one transaction
```

`RecipeTagRunner` then derives the tags and replaces only the `recipe_tag` rows whose `recipe_tag_source` is
`derived`; tags added by hand (source `manual`, also anything added through the API) are never touched, and a
re-run without changes changes nothing. A recipe with an ingredient that has no catalogue item gets neither
vegetarisch nor vegan (unknown is not vegetarian). Dry run by default: it prints the count per tag and 10 example
recipes per tag. `--animal-products` lists which catalogue items block vegetarisch / vegan, and why.

```bash
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.RecipeTagRunner                      # dry run
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.RecipeTagRunner --commit             # one transaction
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.RecipeTagRunner --animal-products
```

Start the REST API (`de.dahaem.api.DahaemApplication`, the jar's `Main-Class`) with:

```bash
java -jar target/dahaem-1.0-SNAPSHOT.jar
```

It is a thin layer over the existing services (no JPA; the services still use `Database`), on port 8080 by default (`--server.port=...` to change it).

### Logging in

Every `/api/**` endpoint requires a login; the PWA shows a login screen and the static files stay public. There is no sign-up page: the operator creates the accounts on the server with `UserAdminRunner`, against the same database the API uses (`schema.sql` already contains the `household` / `app_user` tables):

```bash
java -cp target/dahaem-1.0-SNAPSHOT.jar \
     de.dahaem.auth.UserAdminRunner create-user anna@example.de Anna "Familie M"
java -cp target/dahaem-1.0-SNAPSHOT.jar \
     de.dahaem.auth.UserAdminRunner set-password anna@example.de
```

The password is prompted for twice (at least 10 characters), or read from `DAHAEM_PASSWORD` for scripted setups; it is never a command-line argument. The first user creates the household and becomes its `ADMIN`; everyone after joins that household as `MEMBER`. Passwords are stored only as BCrypt hashes.

Login is a normal session cookie. State-changing requests need the session's CSRF token: `GET /api/auth/csrf` returns `{"headerName", "token"}`, send the token in that header.

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/auth/csrf` | CSRF token for the next POST/PUT (public) |
| POST | `/api/auth/login` | form fields `email`, `password`; 204, or 401 on wrong credentials |
| POST | `/api/auth/logout` | ends the session; 204 |
| GET | `/api/auth/me` | the logged-in user and their household |

The API binds to `127.0.0.1` by default. To use it from phones on the network, put it behind a TLS reverse proxy; see [docs/VISION.md](docs/VISION.md#running-it-on-the-network).

### Endpoints

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/inventory` | all items |
| GET | `/api/inventory/expiring?days=7` | items expiring within N days |
| GET | `/api/inventory/low-stock?threshold=1` | items at or below N in stock |
| GET | `/api/recipes` | recipe list; each has `servings` and `servingsMax` (upper bound of a "3 – 4 Personen" range, `servings` being the lower bound; null when not a range) and `tags` (the tag names, sorted by group then name; `[]` when untagged) |
| GET | `/api/recipes/{id}[?people=N][&basis=servings\|max]` | recipe with nutrition (per serving), ingredients, instructions, `servings` and `servingsMax`. Each ingredient has a decimal `quantity` (0.5 Stück, 2 EL) and `toTaste`; a to-taste ingredient ("Salz nach Geschmack") has `quantity` and `scaledQuantity` null and an empty unit. With `people` (>= 1, else 400) `scaledQuantity` = quantity × people / servings (2 decimals). `basis=max` scales a range recipe from `servingsMax` instead (people / servingsMax) and is ignored when `servingsMax` is null; `basis` in the response says which one was used (`"servings"` or `"max"`); any other `basis` is 400. `tags` lists the recipe's tags as `{name, category, source}` (`source` is `"manual"` or `"derived"`). `scalable` is false when the servings count used is 0. Each instruction keeps the stored `instruction` and has `scaledInstruction`: the step with its amounts for `people` (and `basis`) — a bracket always means 2 / 3 / 4 people in every recipe (`1 EL [1,5 EL \| 2 EL] Butter*` → `1½ EL Butter*` at 3, `2 EL` at 4; a single value is the 4-person one), any other head count scales the front (2-person) value;  plain number + measuring unit amounts (g, kg, ml, l, EL, TL, Prise, Msp.) scale too, °C / times / Stufe / cm / counts never; without `people` each bracket is resolved for the recipe's own head count (`servings`, or `servingsMax` with `basis=max`), so the steps match the ingredient list — a 2-serving recipe shows the front value as before; ⅛ is shown for spoons, pinches and counts; unreadable text stays as written (`StepAmounts`); 404 if unknown |
| GET / POST | `/api/recipes/{id}/tags` | list tags / add `{"name", "category"}` (a hand-added tag is `manual`; adding one the tagger derived makes it `manual`) |
| GET / POST | `/api/meal-plans` | list plans with their recipes / create `{"weekStartDate"}` |
| POST | `/api/meal-plans/{id}/recipes` | add `{"recipeId", "mealTime"}` |
| GET | `/api/shopping-list?recipeIds=1,2[&people=N]` | build (not save) a shopping list; each line carries every current store option (cheapest till price first), never a pre-picked one; to-taste ingredients are left out; `people` scales each recipe from its `servings` (a range's lower bound) |
| PUT | `/api/shopping-lists/{listId}/items/{itemId}/store-item` | set the store product the user picked for a persisted line, `{"storeItemId": 5}`; `null` clears it |
| GET / POST | `/api/purchases` | list / record `{"storeItemId", "quantityPurchased", "pricePaid", "expirationDate", ...}` |
| GET | `/api/analytics/spending[?from=&to=]` | total spent, optionally in a date range |

Errors come back as `{"status", "error", "message"}`: 401 when not logged in, 403 for a missing CSRF token, 400 for invalid input, 404 for an unknown id, 409 when a write violates a database constraint (duplicate week, recipe already on the plan, unknown store item).

The database lives in MariaDB and is not part of the repo. Copy `config/stores.properties.example` to `config/stores.properties` and fill in your own store id and postal code.

## Project status

Working:

- Inventory catalog and read model
- Recipe model (ingredients and instructions) with sample data
- Store importers: ALDI range and offers, Penny offers, aggregator offers for five chains
- Raw to verified promotion (`store_item`) and the flyer to `store_discount` pipeline
- Offer provenance and duplicate-listing resolution

Planned:

- Shopping list combining recipes and low stock against current discounts
- Purchase and finance tracking with receipts as ground-truth prices
- A responsive web UI, self-hosted, replacing the console runner
- An automated test suite

## A note on data

This repo contains the application and the importers, not the pulled store data and not imported third-party recipes. Store data is regenerated by running the importers against the live sites. The sample recipes are written from scratch.

## The name

DAHÄM is the Mannheim / Kurpfalz dialect form of *daheim*. The project takes its name from the area where the author's life in Germany began.
