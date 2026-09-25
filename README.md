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

Java 17, Maven, SQLite via `sqlite-jdbc`, Jackson for JSON. `mvn package` builds one runnable jar with the maven-shade plugin, so the server needs only Java and that jar.

**SQLite instead of a client/server database** because the design target is a single self-hosted box for two users, not multi-tenant scale.

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
scripts/importer/          Python data-prep tools (recipe import, matching, catalog build)
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

Requires Java 17+, Maven, and Python 3.

```bash
mvn package
python setup_db.py
```

`setup_db.py` builds `data/kitchen_organizer.db` (the default path `Database` opens) from
`schema.sql`, `seed/inventory_catalog.sql`, the sample recipes, and `seed/demo_data.sql`, so a
fresh clone has something to run against without any manual DB setup. It refuses to overwrite
an existing database unless you pass `--force`, and takes `--db <path>` to build elsewhere. The
store/brand/price/discount rows in `seed/demo_data.sql` are entirely fabricated for the demo —
see the note at the top of that file — not real pulled store data.

Run the console entry point from your IDE (`de.dahaem.Main`), or a store importer from the command line:

```bash
java --enable-native-access=ALL-UNNAMED -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.stores.StoreImportRunner offers all
```

The application uses a local SQLite database that is not committed to the repo. Copy `config/stores.properties.example` to `config/stores.properties` and fill in your own store id and postal code.

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
