# Backlog

Work that has been decided or identified but deferred. One place for Nate, Claude sessions
and agents. Add an item when you defer something (with the reason and where it came from);
move it to **Done** with the PR number when it ships. Nate decides priority and scope.

Status: `in progress` · `next` · `later` · `idea`

## Recipes

- **Recipe quantity repair (apply)** — `in progress`. Overnight run matched 10,868 / 11,070
  recipes. Before applying, the tool gets Nate's rules (2026-10-11): an amountless ingredient
  line takes its amount from the cooking steps (HelloFresh notation `1 EL (1,5 EL / 2 EL)` =
  2 / 3 / 4 people, several mentions summed); `Stück X` without a number = 1 Stück (e.g. pita,
  pomegranate); `nach Geschmack X` = to taste; only salt, pepper, oil and vinegar become to taste
  when no amount exists anywhere; everything else goes to review. Then one guarded apply.
- **Servings ranges** — `in progress`. Keep "3 – 4 Personen" as a range: `servings` = 3 (lower
  bound, default) + new nullable `servings_max` = 4. Normal recipes stay 2 (all scraped recipes
  are 2-person); explicit "für 4 Personen" / "Bundle 4P" = 4. Recipe 28614 ("OTP 4P … 2
  Portionen") needs a manual decision.
- **Amounts in cooking steps** — `next`. The API reads the bracket notation and returns the
  step text for the chosen number of people (2 → first, 3 → second, 4 → third; other counts
  scale from the 2-person value). Only number + measuring unit is scaled, never °C, minutes,
  cm or step counts. Amounts for things not in the ingredient list (water) stay and scale.
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
- **Offer price-change history** — `later`. A re-pull in the same week overwrites the price,
  so a mid-week price change is lost. Record changes (like `store_price_history` for ALDI)
  for trend analysis.
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
