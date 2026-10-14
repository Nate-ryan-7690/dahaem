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
texts are personal data: keep them outside the repo. Plain "App-Coupon" and brand coupons ("Ferrero Kinder
25%") are booked on the product before them; PENNY's group coupons ("App-Coupon 10% Waren") belong to no
single product and are stored without a line. A discount that would make a line's paid total negative is
stored without a line too, and the report lists it.

```bash
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.receipts.ReceiptImportRunner /path/to/receipt-texts            # dry run
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.receipts.ReceiptImportRunner /path/to/receipt-texts --commit
```

Names left unmatched can be mapped later without importing again: edit `config/receipt_overrides.json`
(move a key from `review` to `exact`, or add one — keys as the import report prints them), then
`--rematch` re-runs the matcher on the stored lines. It looks only at lines whose `match_source` is NULL,
`rule` or `override` — a line set to `manual` by hand is never changed — and updates item, match source
and brand where the result differs; a key moved to `review` clears its former match. Dry run by default
(per changed line old → new item, counts); `--commit` writes every change in one transaction. Receipts,
adjustments, stock and `purchase_history` are never touched; override ids missing from `inventory_item`
are refused.

```bash
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.receipts.ReceiptImportRunner --rematch            # dry run
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.receipts.ReceiptImportRunner --rematch --commit
```

### Recipe tags

Recipe tags come from DAHÄM's own data, in six groups (`tag_category`): **Tier** (Hähnchen, Pute, Geflügel, Ente,
Gans, Rind, Schwein, Lamm, Kalb, Wild, Kaninchen, Pferd, Fisch, Meeresfrüchte — only from CERTAIN animals),
**Ernährung** (vegetarisch, vegan, unklar), **Inhalt** (Alkohol, Fleisch mit Milch), **Zeit** (schnell =
prep + cook ≤ 30 min, total known), **Nährwerte** (kalorienarm ≤ 600 kcal; proteinreich = the EU rule for "high protein", Regulation (EC) No 1924/2006,
Annex: at least 20 % of the energy from protein (protein × 4 / kcal ≥ 0.20) AND at least 20 g protein; all per
serving; thresholds in `config/recipe_tags.properties`, decided by Nate on 2026-10-13 / 2026-10-14) and **Küche** (the recipe's
`recipe_cuisine`, migration 0010). Nothing in the catalogue records which animal a meat item is, so that is its own
table, `inventory_item_animal` (migrations 0011 / 0012), with a `certainty` per animal: `certain` (the item IS from
this animal) or `possible` (a generic item such as Salami, Fleischwurst, Wiener, Lyoner, Leberwurst, Chorizo or
Gelatine, which exists from several animals while the recipe does not say which: Salami may be Schwein, Rind or
Geflügel). A possible animal gives no Tier tag, but it blocks vegetarisch and makes a recipe "anpassbar" for a
household that excludes it (below). The alcohol flag (`inventory_item.inventory_item_alcohol`) marks wine, beer,
spirits, liqueur, cooking wine and the like. Both are filled in two steps with `ItemAnimalRunner` — everything the
name does not say is marked REVIEW and only stored after a person has looked at it:

```bash
# 1. propose animals + alcohol (read-only) -> TSV: item id, name, category, subcategory, certain animals, possible animals, alcohol (yes / no), CONFIDENT / REVIEW
#    Items that already have an animal or the alcohol flag stay out of the proposal; --all proposes them again.
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.ItemAnimalRunner --out items.tsv
# 2. edit items.tsv: fix the columns of a REVIEW row and set its status to REVIEWED
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.ItemAnimalRunner --apply items.tsv            # dry run
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.ItemAnimalRunner --apply items.tsv --commit   # CONFIDENT + REVIEWED rows, one transaction
```

`RecipeTagRunner` then derives the tags and replaces only the `recipe_tag` rows whose `recipe_tag_source` is
`derived`; tags added by hand (source `manual`, also anything added through the API) are never touched, and a
re-run without changes changes nothing. A recipe with a row in `recipe_unmatched_ingredient` (an ingredient line the
importer could not match to a catalogue item, or a recipe whose page could not be checked — see
`scripts/importer/README.md`) is **unklar** and gets neither vegetarisch nor vegan (unknown is not vegetarian); a
recipe without ingredients is unklar too. Dry run by default: it prints the count per tag, how many recipes are
unklar and why (unmatched lines / page not checked / no ingredients), and 10 example recipes per tag.
`--animal-products` lists which catalogue items block vegetarisch / vegan, and why.

```bash
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.RecipeTagRunner                      # dry run
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.RecipeTagRunner --commit             # one transaction
java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.tags.RecipeTagRunner --animal-products
```

### Diet filter (what a household does not eat)

`GET /api/recipes` (and `GET /api/recipes/{id}`) take diet rules, applied on the server so the client never receives
all ~11,000 recipes to filter itself. The PHP passes the rules; the questionnaire ("Was esse ich nicht?" / "Was
esse ich lieber nicht?"), its storage and per person vs household are not part of the API.

- **hard rules** (hide a recipe): `diet=<preset>` (repeatable), `excludeTags=a,b`, `excludeItems=<item ids>`;
- **soft rules** (only mark): `avoidTags`, `avoidItems`: the recipe stays, is sorted after the others and lists what
  matched in `diet.softHits`;
- `includeUnclear=true|false` (default false).

Each recipe is judged as `OK`; `ANPASSBAR` (excluded only through the POSSIBLE animals of generic items such as Salami
or Gelatine, with `diet.hints` per item: item id, name, the animals it may still be — Salami → Rind, Geflügel;
Gelatine → Rind); `HIDDEN` (excluded by a certain tag, animal or item, or by a required tag it lacks: not returned in
the list; `GET /api/recipes/{id}` still returns it with this status); or `UNCLEAR` (a hard rule applies and the recipe
is unklar, i.e. a line of it was not matched: left out unless `includeUnclear=true`, then returned flagged).
`GET /api/recipes/{id}` with rules also lists the recipe's `diet.unmatchedIngredients`. An unknown preset is a 400.

The presets are in `config/diet_presets.json` (no hardcoding): **halal** = exclude Schwein and Alkohol, **koscher** =
exclude Schwein, Meeresfrüchte, Kaninchen, Pferd and Fleisch mit Milch, **vegetarisch** / **vegan** = require that
tag. **The presets filter INGREDIENTS only. DAHÄM cannot certify halal or kosher** (slaughter, certification, what a
product contains beyond its ingredient list; fish without fins and scales cannot be detected yet): meat certification
stays the shopper's responsibility. A recipe is only called OK when nothing in it is excluded and nothing about it is
unknown.

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
| GET | `/api/recipes[?diet=…][&excludeTags=…][&excludeItems=…][&avoidTags=…][&avoidItems=…][&includeUnclear=true]` | recipe list (with diet rules: filtered and sorted on the server, each recipe gets a `diet` object, see "Diet filter"; without rules unchanged); each has `servings` and `servingsMax` (upper bound of a "3 – 4 Personen" range, `servings` being the lower bound; null when not a range), and the page data `description`, `imageUrl`, `thumbnailUrl` (links: the browser loads the image from its source, nothing is copied), `imageSource` (`hellofresh` \| `own` \| `generated`, so images can be replaced in bulk; null without an image), `cuisine` (one German name), `ratingAvg` (0 – 5, 2 decimals), `ratingCount`, `publishedDate` (`YYYY-MM-DD`) — each null when missing (migration 0010, filled by `scripts/importer/enrich_recipes.py`) Also `tags`: the recipe's tag names, sorted by group then name (`[]` when untagged). |
| GET | `/api/recipes/{id}[?people=N][&basis=servings\|max][&diet=…]` | recipe with nutrition (per serving), ingredients, instructions, `servings` and `servingsMax`, and the same page data as the list (`description`, `imageUrl`, `thumbnailUrl`, `imageSource`, `cuisine`, `ratingAvg`, `ratingCount`, `publishedDate`; null when missing). Each ingredient has a decimal `quantity` (0.5 Stück, 2 EL) and `toTaste`; a to-taste ingredient ("Salz nach Geschmack") has `quantity` and `scaledQuantity` null and an empty unit. With `people` (>= 1, else 400) `scaledQuantity` = quantity × people / servings (2 decimals). `basis=max` scales a range recipe from `servingsMax` instead (people / servingsMax) and is ignored when `servingsMax` is null; `basis` in the response says which one was used (`"servings"` or `"max"`); any other `basis` is 400. `scalable` is false when the servings count used is 0. Each instruction keeps the stored `instruction` and has `scaledInstruction`: the step with its amounts for `people` (and `basis`) — a bracket always means 2 / 3 / 4 people in every recipe (`1 EL [1,5 EL \| 2 EL] Butter*` → `1½ EL Butter*` at 3, `2 EL` at 4; a single value is the 4-person one), any other head count scales the front (2-person) value;  plain number + measuring unit amounts (g, kg, ml, l, EL, TL, Prise, Msp.) scale too, °C / times / Stufe / cm / counts never; without `people` each bracket is resolved for the recipe's own head count (`servings`, or `servingsMax` with `basis=max`), so the steps match the ingredient list — a 2-serving recipe shows the front value as before; ⅛ is shown for spoons, pinches and counts; unreadable text stays as written (`StepAmounts`); 404 if unknown `tags` lists the recipe's tags as `{name, category, source}` (`source` is `"manual"` or `"derived"`). With the diet parameters of the list it also has `diet`: `status`, `hints` and `unmatchedIngredients` (`lineNo`, `rawText`, `reason`). |
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
