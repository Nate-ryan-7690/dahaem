# DAHÄM — Project Vision

## What it is

DAHÄM is a self-hosted household organizer for a two-person household: one Java
application on one SQLite database that manages inventory, recipes, meal
planning, shopping lists, supermarket prices and discounts, and spending.

The name is Kurpfälzisch (the Mannheim-area dialect) for *daheim*, "at home."

## Scope

DAHÄM covers **all household shopping, not just food**. The catalog and the
store matching include non-food household goods (cleaning, toiletries, paper
goods, pet, drinks), not only ingredients. "Kitchen" is only the working name
of the original project folder.

## Goals

- Reduce food waste by tracking what is on hand and what is expiring.
- Save money by comparing supermarket prices and current discounts.
- Simplify weekly meal planning by connecting recipes, inventory, and shopping.
- Work mobile-first while actually standing in the shop.

## Design principles

- **Generic item vs store product.** `inventory_item` is the generic thing a
  recipe or the pantry needs ("butter"); `store_item` is a specific product a
  store sells ("MILSANI butter 250g"). Recipes never depend on a brand or store.
- **Raw layer vs verified layer.** Everything pulled from a store lands in a raw
  layer (`store_product`, `store_offer`) exactly as pulled. Only complete,
  catalog-matched products become verified `store_item` rows (what the shopping
  list reads); offers become `store_discount` rows on them.
- **Provenance and trust order.** Every price carries where it came from. Trust
  order is receipt > direct store feed > aggregator.
- **Recency, not cheapest, for duplicate listings.** A store can list the same
  product twice (a stale relisting at an old price and the current one). The
  shopping list shows the most-recently-seen available listing, not the cheapest,
  so it never quotes a price that no longer exists.
- **Pantry staples are tracked.** Salt, pepper, oil, sugar, and flour are real
  ingredients the household owns and re-buys, so they belong in recipes and the
  shopping list. Only water is dropped.

## Data-source strategy (the legal path)

The scrapers in this repo are a personal-use prototype that proved the feature.
For any shared or distributed version, the data sources become licensed and
swappable behind the existing provenance layer:

- **Recipes:** an own-curated base plus user-generated recipes. No redistribution
  of third-party recipe text.
- **Discounts:** an aggregator partner/affiliate API (for example Bonial/kaufDA
  or marktguru) with attribution and deep-links back to the source, so the app
  drives them traffic rather than scraping their data.
- **Purchases:** receipts as the ground-truth price layer (OCR first, structured
  e-receipts or a receipt-aggregator API later).

## Stack and trajectory

- **Now:** Java 17 + Maven + SQLite, one shaded jar, deployed self-hosted on a
  home server for two users. Package-by-feature, strict model/repository/service
  layering.
- **Chosen over** the original Python/Django/PostgreSQL plan partly because Java
  is easier to carry into a **Kotlin Android client** later, reusing the same
  models and backend.
- **Later:** a responsive web UI, then optionally a Kotlin/Android app over the
  same backend/API. A commercial version only with the licensed data sources above.

## Running it on the network

The REST API requires a login (session cookie + CSRF, passwords stored as
BCrypt hashes, accounts created by the operator with
`de.dahaem.auth.UserAdminRunner`; there is no public sign-up). The default in
`application.properties` is still `server.address=127.0.0.1`, so a fresh install
is never reachable from the network by accident.

To use the PWA from phones and tablets (and later the Android client), the
operator may expose it, but **only behind a reverse proxy that terminates TLS**
(Caddy or nginx): the login form sends the password and every request carries
the session cookie, neither of which may travel over plain HTTP. Either keep
`127.0.0.1` with the proxy on the same host (preferred), or bind `0.0.0.0` if
the proxy runs elsewhere, and override:

```
server.address=127.0.0.1                # or 0.0.0.0 for a proxy on another host
server.forward-headers-strategy=native  # trust the proxy's X-Forwarded-Proto
server.servlet.session.cookie.secure=true
```

A minimal Caddyfile: `dahaem.example.de { reverse_proxy 127.0.0.1:8080 }`.

What the login does *not* do yet: all data is still one shared pool. The
`household` / `household_member` tables exist, but inventory, recipes, meal
plans, shopping lists and purchases are not yet scoped per household, which is
fine for the single household this runs for today.

## Roadmap

1. Core household app: inventory, recipes, meal planning, shopping lists.
2. Price and discount intelligence: offers, comparison, price history.
3. Receipts and automation: OCR, review UI, inventory updates from receipts.
4. Smart suggestions: surface recipe and meal-plan options ranked by what is in
   stock, what is expiring, current discounts, nutrition, and household
   preferences. DAHÄM informs the choice; the user always decides. It never
   auto-picks the cheapest plan.
5. Collaboration and polish: household sharing, PWA/offline, notifications.
