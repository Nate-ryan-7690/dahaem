# Backlog

Work that has been decided or identified but deferred. One place for Nate, Claude sessions
and agents. Add an item when you defer something (with the reason and where it came from);
move it to **Done** with the PR number when it ships. Nate decides priority and scope.

Status: `in progress` · `next` · `later` · `idea`

The product / UX plan for the PHP frontend is `docs/ux-product-spec.md` (living brief, started
2026-10-13). Frontend items are coded by hand by Nate; agents build the Java API they need and
document the contract. Its open decisions are settled with Nate one at a time.

## Recipes

- **Step amounts: show in the recipe view (PHP, Nate)** — `next`. Display `scaledInstruction`
  instead of `instruction` (contract in #70).
- **Step amounts: plural units** — `idea`. Units are kept as written, so "1 Prise" scales to
  "2 Prise", "1 Zehe" to "2 Zehe". Plural forms need a small word list; deferred to keep units
  unchanged as decided.
- **Servings ranges: importer, shopping list, household choice** — `next`. (1) `extract.py` /
  `load_recipes.py` draft `recipe_servings` only (a range recipe gets its lower bound and a REVIEW
  flag); the draft SQL that `load_recipes.py` parses needs `recipe_servings_max`. (2) The shopping
  list scales from `servings` (the lower bound) only; `?basis=max` exists on `GET /api/recipes/{id}`
  only. (3) The per-household choice of how to read a range (lower / upper) is not built.
- **Servings from the title is not reliable** — `next`. The title rule ("4P" / "für 4 Personen" =
  4) was wrong for scraped bundles: 28164, 31475 and 32578 ("… Bundle 4P", "Weihnachtsmenü 4P") have
  2-person ingredient lists; Nate approved setting them to servings 2 (guarded live fix, 2026-10-13).
  28615 / 28616 really are 4-person lists. The importer should decide servings by comparing the
  ingredient list with the step brackets (front value = 2 people, last = 4 people), not by the title;
  re-check the other title-derived servings the same way.
- **Servings review: 60 titles** — `next`. 59 ready meals with alternative portions ("1 Portion für
  2 Prs. oder 2 Portionen für 1 Prs.", "2 Portionen als Vorspeise oder 1 Portion als Hauptspeise")
  and 28614 ("OTP 4P … 2 Portionen") stay at servings 2 until Nate decides a rule or edits them.
- **"Brühe" family rule for the repair tool** — `next`. A step's generic `Brühe` / `Gemüsebrühe`
  should count for a line `Stück Gemüsebrühe` (or Geflügel-, Rinderbrühe) when the recipe has
  exactly ONE stock item, like `Öl` ↔ `Olivenöl` and a generic `Essig` / `Senf`
  (`MERGE_FAMILIES` in `scripts/importer/step_amounts.py`). In the 2026-10-11 run 32 lines went to
  review for this ("100 ml Brühe zubereiten" vs `Stück Gemüsebrühe`); with several stocks in the
  recipe it stays a review. Source: Nate, step 3 of the recipe repair.
- **Remaining wrong rows after the repair** — `next`. ~301 ½-bug rows held because of uncertain
  item matches (mostly "Limette, gewachst", "Zitrone"): need overrides. 12 rows in ambiguous
  recipes. Unitless lines read as Stück ("75 Weizenmehl", "25 Sojasoße", "25
  Aprikosenchutney"): manual override.
- **Exact 3 / 4-person ingredient amounts** — `idea`. HelloFresh's own per-size amounts are not
  always linear ("1 Packung" for 2 and 3 people). Only available per size on the site / in the
  PDFs, not in the scraped 2-person data.
- **Display labels (PHP, Nate)** — `later`. Show "nach Bedarf" for to-taste items (fits salt and
  butter); fractions (0,5 / ½) for Stück, whole numbers for g/ml.

## Offers, matching and catalogue

- **"versch. Sorten" / multi-item offers** — `next`. One offer counts for every item it covers,
  but only the variants that exist in the ADVERTISED PACK SIZE (Coca-Cola 1,25 l ≠ cans). Named
  variants ("A / B", "oder", commas) are split directly. Otherwise look up brand + size once,
  in this order: own data (earlier offers, ALDI range) → Open Food Facts API → brand / store
  product pages → AI lookup with web search; save the result, confirm when unclear. No
  scripted Google searches. Pasta covers shapes AND lines (Vollkorn, glutenfrei). ~93 offers
  waiting (detergents, water, milk, Fanta / Sinalco, pasta, chocolate, wine).
- **Conversion table** — `next`. Per-item piece weights and densities (1 Fischstäbchen ≈ 30 g,
  1 Kiri portion, 1 aubergine, Corny ≈ 25 g …), EL ≈ 15 ml / TL ≈ 5 ml for liquids and per-item
  grams for solids, wash loads (WL). Releases correct matches held only for the unit
  (Fischstäbchen 15 Stück, Kiri 6 Stück, Mars 5 Stück, Auberginen 1-kg-Netz, Mayonnaise /
  Ketchup in ml, Buttermilch 500 g, Heidelbeeren 580 ml, Underberg 12 Stück, piece-only pizzas,
  gram-only Waffelschnitte packs). Also needed for buyable shopping-list amounts.
- **Catalogue batch 2** — `next`. Items flagged as possibly too coarse under the "product minus
  brand" rule: Kräuterlikör / Bitter, Mixgetränk (alkoholisch), Anisspirituose (Ouzo vs
  Pastis), Schokoriegel, Tiefkühlpizza, Müsliriegel, Instantnudeln. Overrides for
  "(Oliven-)Öl*" (95 recipe lines). Top unmatched recipe ingredients (Burger Sauce, süße Soße
  asiatische Art, Spekulatius-Kekse, vegane Produkte, Kaminkäse, …). Non-food recipe lines
  (Holzspieße, Schaschlikspieße) → skip.
- **Brand list** — `later`. Brand recognition only knows single-word brands, so aggregator
  offers like "Johnnie Walker Black Label" get "Ohne Marke". Curated multi-word brand list;
  clean up invented brand rows ("Fischstäbchen", "Deutsche", "BIO", fragments) and spelling
  duplicates (KING'S CROWN / KING’S CROWN, LANBECK / LANDBECK, …).
- **Aggregator per-offer end dates** — `idea`. The page header gives the week (Mon–Sun); exact
  "bis …" dates are only on each offer's own page. Fetch only offers not seen before.
- **Seed sync** — `later`. 9 live inventory items are missing from `seed/inventory_catalog.sql`
  (ids 129, 1034, 1179, 1216, 1218, 1431, 1432, 1433, 1505); export them into the seed.
- **Old SQLite tools** — `later`. `scripts/importer/add_ingredients.py` and `dedup_catalog.py`
  still target SQLite; port or retire.

## Receipts and purchases

- **Receipts → purchase history** — `in progress`. 22 Penny receipts (June–Oct 2026, PDFs) parse to
  the cent (#64); read-only analysis: 541 lines, 300 distinct names, €1,324.68. Receipts get their OWN
  tables (`purchase_receipt`, `purchase_receipt_line`, `purchase_receipt_adjustment`, migration 0008) —
  NOT `purchase_history`, which doesn't fit (store_item / expiration date NOT NULL, INTEGER quantity, no
  receipt link). Every line is stored, matched or not; coupons are adjustments on the product before
  them. `ReceiptImportRunner` (dry run, `--commit` = one transaction, duplicates by store + date-time +
  receipt number) never calls `PurchaseService` and never changes stock: the household's stock is built
  from new purchases and check-ins, not reconstructed from old receipts. First dry run on Nate's 22 receipts
  (2026-10-13): 22 NEW, all balancing, €1,324.68. Fixed before the real `--commit`: PENNY group coupons
  ("App-Coupon 10% Waren", one per tax class, printed after an unrelated product or as a block at the end)
  are stored without a line instead of on the product before them; a discount that would make a paid total
  negative is stored without a line and reported. New items 1818–1821 (migration 0009) and override
  changes per Nate's decisions. Next (llmbox, Nate): snapshot → apply 0009 → rebuild → dry run → `--commit`.
  Uses: real regular prices (Penny publishes mostly UVPs), confirmed product → item matches, price
  history and budget.
- **Receipt review** — `next`. Names left unmatched on purpose for Nate to map (they are in the
  `review` list of `config/receipt_overrides.json`, so the name rules can't guess them): CHEDDAR
  HERZHAFT, KERRYGOLD CHEDD., MINI GEFL WIENER (poultry, not the pork Wiener), LAYS S.CREAM&ON. (not
  the salted chips), Kinder Riegel, MOZZARELLA-STICK / LH MOZZ. STICKS; and the analysis's wrong rule
  matches: VIT.Z. RASPBERRY (≠ Himbeere), Krom FB Kirsche (Krombacher Fassbrause ≠ Kirsche), SALATSCHALE
  ORZO (≠ Orzo pasta), FRUCHTZWERGE (≠ Naturjoghurt; `store_overrides.json` maps "fruchtzwerge" to 386,
  which is why the receipt import does not use the store overrides).
  From Nate's decisions on the first dry run (2026-10-13): **BACON / DEL. BACON / BACON XXL** moved from
  `exact` (→ 350) to `review` — Nate checks the actual product (sliced / block / bits) before mapping;
  BACON-STREIFEN → 1536 stays. **C & W Brötchen** — what product is it? ("C & W" looks like a brand.)
  Still to confirm: Bagels Mehrkorn → 587 ("bagel vollkorn": Mehrkorn ≠ Vollkorn in wording).
  The ~200 remaining unmatched names of the dry run are mapped AFTER the `--commit`, without re-importing:
  edit the overrides (move a key from `review` to `exact`, or add one), then `ReceiptImportRunner --rematch`
  (dry run) and `--rematch --commit`. A line can also be fixed by hand with `match_source = 'manual'`; the
  rematch never changes those.
- **Rosinenbrötchen unit** — `later`. "Rosinen Brötchen" now maps to the existing 575 rosinenbrötchen,
  whose unit is `g` like most rolls; 569 Brötchen and Franzbrötchen were changed to `Stück` in 0004.
  Rolls are bought per piece; switch 575 (and the other rolls) when Nate confirms. Left out of 0009,
  which only adds.
- **Sour Cream vs. Sauerrahm-Dip** — `idea`. 1821 Sour Cream (new, dairy) sits next to 1522 "sauerrahm
  dip" (snacks / dips & spreads) and 368 saure sahne. If Penny's "SOUR CREAM" is really the dip, map it
  to 1522 instead (rematch).
- **Receipts + shopping list** — `idea`. Once the PHP shopping list is in use, match a new receipt's
  lines first against that trip's open list (`shopping_list_item` already has `inventory_item_id`,
  `store_item_id` with brand, `purchase_status`). Confirmed pairs tick the list item off with the real
  price / quantity, take the brand from the list's `store_item` and are saved as receipt overrides.
  Surface differences (on the list but not bought / bought but not on the list), never decide. A store
  offer valid that day at the same price is a second brand hint. New (live) receipts then DO add to
  stock; only the historical import must not.
- **Receipt parser cleanup** — `later`. Two receipts leave the paid total on its own line in
  `unparsedLines` (harmless, no payment data).

## Inventory and shopping list

- **Inventory check-in questions** — `idea`. Ask "Hast du noch genug Salz?" / "Wie viel Milch
  ist noch da?" (voll / halb / fast leer); the answer corrects the stock and low items go on
  the shopping list. Needed for to-taste items, whose use can't be computed from recipes.
- **Shopping list vs. stock** — `idea`. Measurable staples (oil, flour, sugar, butter) are
  consumed by recipes; the list shows only the shortfall against stock, in a buyable unit
  ("10 EL Olivenöl" → 150 ml → nothing to buy if 400 ml is at home). To-taste items never
  appear; their restocking runs through the inventory.
- **Portion feedback** — `idea`. After cooking, ask "War das zu viel / genau richtig / zu
  wenig?" — for EVERY recipe (HelloFresh portions vary per recipe). Learns a per-recipe
  adjustment and a per-household factor. For range recipes a household setting ("wir essen
  eher mehr / weniger") picks the lower or upper bound.
- **Coffee machine setting** — `idea`. A household owns one capsule / pad system; show only
  offers for that system.

## API work for the frontend (from the UX spec)

Gaps found 2026-10-13 by reading the current API and schema; agents build these, Nate builds the pages.

- **API audit for the UI** — `next`. Go through every page in the spec (Login, Home, Meal Planner,
  Recipes, Shopping list, Inventory, Offers) and list each read / write it needs against the existing
  endpoints; record each missing one here. Known so far: meal plans can only be listed, created and
  appended to; inventory is read-only; a shopping list can't be saved, ticked off or completed; there
  are no user / household settings.
- **Meal plan: any day, any number of meals** — `next`. `meal_plan_recipe` has no date, its key
  (recipe, plan) blocks the same recipe twice in a week, and `meal_time` is a fixed slot text. Needs
  an entry id, a date, an order within the day, servings per entry and an optional free label (no
  breakfast / lunch / dinner slots); endpoints for one week by date, add / edit / remove / move.
  Migration with a data move for existing plans → snapshot first, guarded.
- **Recipe search and paging** — `next`. `GET /api/recipes` returns all ~11,000 recipes. Server-side
  search (name, ingredient), filters on data that actually exists (time, tags, servings), paging, and a
  small card DTO; the planner's recipe picker uses the same endpoint.
- **Recipe images** — `in progress`, this PR (migration 0010, `enrich_recipes.py`). Images are LINKED, never
  copied: `recipe_image_url` / `recipe_thumbnail_url` hold the URLs and the browser loads them from
  HelloFresh; `recipe_image_source` (`hellofresh` | `own` | `generated`) lets all of them be replaced in
  bulk later (bought licence, own photos or AI-generated). The PHP shows a designed fallback when there is
  none. Next (llmbox, Nate): snapshot → apply 0010 → rebuild `dahaem-api` → `enrich_recipes.py` → review →
  apply the SQL in one transaction. Same PR adds description, cuisine (German, `config/cuisine_map.json`),
  rating and publish date.
- **1,294 cached recipes never imported** — `later`. The page cache holds 12,162 pages; 10,868 tie to a DB
  recipe, 1,294 have none (read-only check 2026-10-13). Decide whether to import them (extract + load) or
  drop them from the cache; `enrich_recipes.py` only counts them.
- **Allergens** — `idea`. Needs allergen data per item (the pages hold no allergens, diet flags or tags);
  nothing to show or filter on until the catalogue carries it.
- **Recipe opportunities with evidence** — `next`. `/api/suggestions/by-savings` and `/by-expiring`
  exist; extend them so each suggestion says which ingredients are covered by which offer (store,
  product, price, valid until), which are already in stock and which are still uncovered. Suggestions
  never change the plan by themselves.
- **Shopping list lifecycle** — `next`. Save a list (from a meal-plan week), tick items, record the
  shop as purchases (ties in with the receipt import and "Receipts + shopping list"); on refresh warn
  when a chosen offer has expired or a price changed, never substitute silently.
- **Inventory writes** — `next`. Add, consume, correct and expiry changes with a history, so low /
  expiring items can lead to "adjust stock", "plan a recipe" or "add to the list".
- **Auth and session review** — `next`. Check PHP sessions against the Java login / me / CSRF
  endpoints together: session expiry, logout, errors that reveal nothing internal. Account creation /
  invitation is open (spec decision 6); no public self-registration until it is decided.
- **Stores and pinned stores** — `later`. A store list with name and logo for the Home offers deck and
  "offers of one store"; per-user pinned stores change only the order, never hide stores or offers.
  Store logos / banners: check the usage rights, fall back to a neutral DAHÄM card.
- **Personal vs household settings** — `later`, needs Nate's decision (spec decision 7). Greeting,
  language, theme per user; plan, stock, list and purchases shared per household.

## Platform

- **Support tickets: backend foundation** — `next` per the spec (it says "do now"; Nate sets the
  order). Tickets, messages (user-visible vs staff notes), access requests, explicit time-limited
  grants bound to one ticket and household, append-only audit log; default deny. The negative tests in
  spec §12 (no ticket, no grant, wrong household / ticket, expired, revoked, closed, wrong role) are
  acceptance criteria. Staff access to household content stays disabled until that gate passes.
  Large and independent of the UI work. Policy values (scopes, grant duration, retention) still open.
- **Household isolation** — `later`. Every query and endpoint scoped to the user's household, with
  tests that one household can never read or change another's data (needed before any hosted use).
- **Licence** — `later`, Nate's decision. Neither repo has a `LICENSE`; decide before making either
  repo public or accepting outside contributions.
- **Monetization** — `idea`, nothing chosen (spec §12). Privacy is a baseline: household data is never
  sold or used for ads.

## Frontend and mobile (PHP, Nate)

- **Design system** — `next`. Fresh & Natural direction is decided; compare a sage-led palette (warm
  off-white, optional olive accent) with an olive-led one on real screens (login, home, week planner),
  check contrast and colour-blindness, then fix tokens, typography, spacing, components, light / dark.
- **Login and app shell** — `next`. Login (show / hide password, clear errors, logout, session
  expiry), shared layout, navigation, design tokens, loading / empty / error states.
- **Home** — `next`. Meal plan first (compact week, images with fallback), then recipe opportunities
  with their reasons, store offers deck (pinned stores first; one store at a time on mobile, desktop
  open), one compact "Household essentials" summary.
- **Meal Planner page** — `next`. Whole week on desktop, a day view on mobile, any number of meals
  per day, recipe picker over the calendar, previous / next week. Needs "Meal plan: any day, any number
  of meals" and "Recipe search and paging".
- **Recipes page** — `next`. Search, paging, filters, cards, detail with `?people=N`,
  `scaledQuantity` and `scaledInstruction`; actions "add to plan" and "add to shopping list".

- **Mobile = PHP frontend as a mobile-first PWA** — decided 2026-10-10. Installable on the
  home screen, offline shopping list via a service worker (the old Java static app had this:
  `sw.js`, manifest). No native app for now; the internal-only API stays internal.
- **`sql/schema_mariadb.sql` in dahaem-frontend** — `next`. Still has the old INTEGER recipe
  quantity; align with migration 0005 (and later `servings_max`).
- **Recipe view** — `next`. Uses `GET /api/recipes/{id}?people=N` (contract in PR #61 / #62);
  part of the Recipes page above.
- **Shopping list, Inventory, Offers pages** — `later`, after their API items above.
- **Help & Support pages** — `later`, after the ticket backend.
- **Culinary inspiration** — `idea`. Discovering dishes from around the world, baking projects,
  techniques, trustworthy nutrition content, independent of offers; not in the first release (spec §2A).
- **Household chores** — `idea`, Nate's decision. Recurring upkeep only (bins, litter tray, filters),
  not a general to-do app.

## Process

- **Agent rules in CLAUDE.md** — `idea`. Measure first (read-only), then fix; surface contract
  changes (schema, API, units, item definitions) for Nate's decision instead of silently
  redefining them; every PR names its tests and verification.

## Done

- #59 Override full-name lookup, 0 = excluded, overrides skip the price check; parser: capital I
  litre, WL, multipacks, ranges use the lower number, broth yields; ALDI sizes; 0 € offers to
  review; review reports with store, sale window and flyer URL.
- #60 Catalogue batch 1 (120 items, overrides, guarded migration) — applied live 2026-10-10.
- #61 / #62 / #63 Recipe scaling (`?people=N`), decimal quantities + to-taste, MariaDB importer,
  repair tool with name matching — migration 0005 applied live 2026-10-10; 48 recipes repaired.
- #64 Penny receipt parser: discount-excluded items, deposit variants, footer, payment data
  never stored — 22 / 22 real receipts balance.
- #66 Offer price-change history: `store_offer_price_change` (migration 0007), written by
  `saveOffers` in the same transaction when a re-pulled offer's price changes — migration applied live 2026-10-11; the first live
  `refresh weekly` after it re-priced 0 of 3,636 offers, so no row has been written yet.
- #67 / #68 Recipe repair applied live 2026-10-11 (snapshot `dahaem-20261011-113126.sql`, migrations
  0006 + 0007, one transaction, final state compared row by row before COMMIT): step amounts
  for amountless lines, `nach Geschmack` / staples = to taste, countable `Stück X` = 1 Stück,
  servings from titles + `recipe_servings_max`. `recipe_ingredient` 104,673 → 140,464, to-taste
  113 → 21,165, source URLs 48 → 10,868, servings_max on 77 recipes. Left for review in the TSV:
  415 amountless lines (126 covered by a numbered line, 122 mixed units, 79 no amount anywhere,
  43 steps amount already taken, 45 unsure wording / several candidates) and 60 servings titles
  (59 "… oder …" ready meals + 28614), all still at servings 2. Still open: importer writes no
  `servings_max`, per-household reading of ranges, Brühe family rule (above).
- #70 Amounts in cooking steps: `GET /api/recipes/{id}` instructions gain `scaledInstruction` for
  `people` / `basis` (`StepAmounts`). A 2-serving recipe uses HelloFresh's own bracket values for
  2 / 3 / 4 people (single-value bracket = 4 people), other counts and other recipes scale the
  2-person value; plain number + measuring unit amounts scale, °C / times / Stufe / cm / %, Watt,
  plain counts and pack sizes (`1 Dose (400 g)`, `2 x 100 g`) never; word brackets and brackets of
  times, temperatures and settings (unit before or after the bracket, or `Stufe` before the number:
  `10 [12] Min.`, `200 [220] °C`, `Stufe 2 [3]`) are picked per size, never scaled; unreadable
  brackets and a missing `]` stay as written. Tests on made-up steps only.
- Step brackets follow 2 / 3 / 4 people in every recipe (follow-up to #70): front = 2 people, middle = 3
  (two values only), last = 4 (a single value = 4); other head counts scale the front value from 2. Without
  `people`, brackets resolve for the recipe's own count (`servings`, or `servingsMax` with `basis=max`) so
  steps match the ingredient list; 2-serving recipes unchanged. Amounts without a bracket scale as before.
  0.125 shows as ⅛ for spoons, pinches and counts. No schema or data change.
