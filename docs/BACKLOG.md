# Backlog

Work that has been decided or identified but deferred. One place for Nate, Claude sessions
and agents. Add an item when you defer something (with the reason and where it came from);
move it to **Done** with the PR number when it ships. Nate decides priority and scope.

Status: `in progress` · `next` · `later` · `idea`

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

- **Receipts → purchase history** — `next`. 22 Penny receipts (June–Oct 2026, PDFs) parse to
  the cent (#64). Next: copy them to llmbox (gitignored, personal data, never in the repo);
  read-only analysis of how many distinct receipt names exist and how many already match;
  review the abbreviations ("LH FRISCHK.NATUR", "H-SCHMAND OGT") once; import into
  `purchase_history` with duplicate protection via the receipt number; snapshot first.
  Uses: real regular prices (Penny publishes mostly UVPs), confirmed product → item matches,
  consumption for the inventory, price history and budget.
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

## Frontend and mobile (PHP, Nate)

- **Mobile = PHP frontend as a mobile-first PWA** — decided 2026-10-10. Installable on the
  home screen, offline shopping list via a service worker (the old Java static app had this:
  `sw.js`, manifest). No native app for now; the internal-only API stays internal.
- **`sql/schema_mariadb.sql` in dahaem-frontend** — `next`. Still has the old INTEGER recipe
  quantity; align with migration 0005 (and later `servings_max`).
- **Recipe view** — `next`. Uses `GET /api/recipes/{id}?people=N` (contract in PR #61 / #62).

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
