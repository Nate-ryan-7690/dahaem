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
- **Recipe tags derived from our own data** — shipped in #77 (migration 0011): Tier, Ernährung, Zeit,
  Nährwerte and Küche from `inventory_item_animal`, the ingredients, the times and the nutrition, written
  by `RecipeTagRunner` as `recipe_tag_source = 'derived'`; manual tags are never touched. Thresholds
  confirmed by Nate 2026-10-13: schnell ≤ 30 min (total time, prep + cook > 0), kalorienarm ≤ 600 kcal,
  proteinreich ≥ 30 g protein, per serving (`config/recipe_tags.properties`). **Changed 2026-10-14:** proteinreich
  is now the EU "high protein" rule (Regulation (EC) No 1924/2006, Annex: ≥ 20 % of the energy from protein) AND
  ≥ 20 g protein per serving (`high-protein.energy.share`, `high-protein.min.grams`).
- **Diet filter: what a household does not eat** — `in progress`, this PR (migration 0012). New Tier values
  (Geflügel, Ente, Gans, Kaninchen, Pferd); POSSIBLE animals for generic items (Salami, Fleischwurst,
  Wiener, Lyoner, Leberwurst, Chorizo, Gelatine …: no Tier tag, but they block vegetarisch and make a
  recipe "anpassbar"); the alcohol flag; Inhalt tags (Alkohol, Fleisch mit Milch); `unklar` for a recipe
  with an unmatched ingredient line; presets in `config/diet_presets.json`; `GET /api/recipes` /
  `/api/recipes/{id}` take hard rules (`diet`, `excludeTags`, `excludeItems`), soft rules (`avoidTags`,
  `avoidItems`) and `includeUnclear`, and answer with the status OK / ANPASSBAR / UNCLEAR (HIDDEN recipes
  are not returned). Presets filter INGREDIENTS only; meat certification (halal / kosher slaughter) stays the
  shopper's responsibility. Nate still has to: snapshot, apply 0011 + 0012, rebuild `dahaem-api`, run
  `find_unmatched_ingredients.py`, the animal review below, the tagger (run order in the PR description).
  Decided in the PR, worth a look: HIDDEN under vegetarisch/vegan for a recipe whose only problem is a
  possible animal (Gelatine: no allowed animal for a vegetarian), and a recipe without ingredients is unklar.
- **Item diet review** — `next`, Nate. `ItemAnimalRunner` proposes, as a TSV, the certain animals, the POSSIBLE
  animals (Salami: Schwein, Rind, Geflügel; Gelatine: Schwein, Rind; Schmalz: Schwein, Gans; Talg: Rind,
  Lamm; Worcestersauce: Fisch …) and the alcohol flag; only names that say the animal (Entenbrust,
  Kaninchenkeule, Pferdewurst, Geflügelbrust) and alcoholic drinks by subcategory are CONFIDENT, the rest is
  REVIEW. Nate decides: which possible animals each generic item really has, which items contain alcohol
  (cooking wine, Mirin, Reiswein, wine pastes …), and the items left without any animal (a meat item the name
  does not explain, e.g. a canned stew). Items added to the catalogue later show up in the next proposal;
  `--all` re-proposes the classified ones. Horse is disputed for halal and is not in the halal preset;
  add `excludeTags=Pferd` per household if wanted.
- **Vegetarian / vegan exclusion list: review** — `next`, Nate. `AnimalProducts` blocks the tags by
  category, subcategory and name (listed in the PR; `RecipeTagRunner --animal-products` prints it).
  It errs towards blocking; extend or loosen it there. Things it cannot know: rennet in cheese,
  gelatine in sweets, bone char in sugar, milk powder in bread or chocolate.
- **Unmatched recipe ingredients** — `next`, Nate. `recipe_ingredient` cannot hold a line without a catalogue
  item, so the importer used to drop it and a recipe with a dropped "Speck" line looked complete. Now they are
  stored in `recipe_unmatched_ingredient` (migration 0012): `find_unmatched_ingredients.py` for the recipes
  already imported (report: the 60 most common unmatched names), `load_recipes.py` for new ones. A recipe with
  any row is "unklar" and never vegetarisch / vegan. Nate: map the top unmatched names (overrides or new
  catalogue items). **Rematch built** (`rematch_unmatched.py`, follow-up to #79): sorts every stored unmatched line
  into inserted / covered / non-food / no item yet / review (rules in `scripts/importer/README.md`); Nate runs it on
  the server (run order in the PR), reads the REVIEW TSV, applies the SQL in one transaction, then `RecipeTagRunner`.
  202 recipes have no URL and 'unchecked' stays until someone checks them by hand. Names Nate's 2026-10-14 run
  will most likely land in the REVIEW TSV (the importer's fuzzy `like` match is unsure; each needs an override or
  a catalogue item): `pflanzliche Margarine` (157 lines; `like` → Margarine, but there is also pflanzliche Butter),
  `gehackter Knoblauch & Ingwer in Öl` (48), `Honig-Senf-Dressing` (37), `Ziegenfrischkäse-Crumble mit Honig` (44),
  `eingelegter grüner Pfeffer` (21; `like` → grüner Pfeffer, a different product).
- **Non-food lines in recipes** — `done` in the rematch (`scripts/importer/non_food.json`). Holzspieße (89 lines),
  Backpapier, Alufolie, Zahnstocher ... are resolved as ignored: not an ingredient, never a diet blocker, never
  unklar. `Schaschlikspieße` counts as the skewers only when a cooking step puts food on them; otherwise it goes to
  review (it may be the prepared meat skewers). New names: add them to the list, the report shows what is left.
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
  **Added 2026-10-22 (catalogue batch 2b):** the rematch converts spoons, ml and dl only for liquids (1 EL = 15 ml,
  1 TL = 5 ml, 1 dl = 100 ml); **g against EL / TL / ml stays a REVIEW** — in the 2026-10-22 dry run that is ~40
  oil lines ("15 g Öl" next to "1 EL Olivenöl") plus the sugar / butter cases, all waiting for this table. A recipe
  line in ml / EL on an item stored in g (Honig-Senf-Dressing 50 ml, item unit g) needs it too.
- **Variant split review** — `next`, Nate. Under the item rule of 2026-10-22 (one item per product on the shelf,
  varieties and flavours included, brand abstracted) earlier work merged varieties: Tiefkühlpizza, Eiscreme,
  Helles / Lager, Kräuterlikör / Bitter, Mixgetränk, Biermix, Fruchtjoghurt, Instantnudeln, Tafelschokolade, the
  flavoured receipt lines (Blackberry Cider → Cider, Paprika-Lyoner → Lyoner, Bagels Mehrkorn → Bagel Vollkorn) and
  20 Brötchen products on one item. `docs/variant_split_review.md` lists each place with the live rows affected and
  a proposed split; **no data was changed**. Nate decides each row (and the open questions at its end: standard
  Senf: decided 937, 1679 a probable duplicate; is "Bio" a variety, Radler vs Biermix for Paulaner Weißbier-Zitrone, 3b wrong links); accepted
  rows become a migration (pattern 0015), override changes and a guarded re-match.
- **Unmatched names under 5 lines** — `later`. Batch 2b gave every no-item name with ≥ 5 lines its own item. After
  the 2026-10-22 dry run 546 lines in ~340 names with 1–4 lines each remain unmatched (largest: Landana Grünes Pesto,
  Bali-Nudeln, Tom Kha Gai Suppe, Dalsuppe, pflanzliche Milchalternative, Birria-Paste, Gemüse-Maultaschen,
  veganes Schmelztopping, Pizzafleischkäse, Stremellachs, „Gute Laune“-Käse — 4 lines each; list:
  `rematch_<stamp>_noitem.tsv`). Add items as they come up in more lines, same rule, same pattern as 0015. Variety
  lines of an existing batch-2b item (e.g. "Tomatiger Bruschetta-Dip") go to review as `like`, not to the plain item.
- **Catalogue batch 2** — `next`. Items flagged as possibly too coarse under the "product minus
  brand" rule: Kräuterlikör / Bitter, Mixgetränk (alkoholisch), Anisspirituose (Ouzo vs
  Pastis), Schokoriegel, Tiefkühlpizza, Müsliriegel, Instantnudeln. Override for
  "(Oliven-)Öl*" → Olivenöl 1022 added with the rematch. Top unmatched recipe ingredients (Burger Sauce,
  Kaminkäse, vegane Produkte, …). Non-food recipe lines (Holzspieße, Schaschlikspieße) → `non_food.json`, no
  item needed. **First rematch run (2026-10-14): 351 names with no item, 1,070 lines.** The 60 most common, lines each (no item
  after PR #80; the ones marked *done* are handled by migration 0014 or the overrides of the follow-up PR):
  Süße Soße Asiatische Art 22 · Spekulatius-Kekse 21 · Gemüsemix Lauch, Champignons, Karotte 18 · Veganes Filet
  nach Lachs-Art 18 *done* · Rescued Bruschetta Dip 16 · Planted veganes Pulled in BBQ-Marinade 14 *done* ·
  Champignons und Lauch, geschnitten 13 · Bauerngarten Bio-Käse 13 · Burger Sauce 13 · Veganes Western Dressing 13 ·
  Asian Stir Fry Sauce 12 · Polpa Chili 12 · Mini-Spekulatius 12 · Himbeer-Balsamico-Dressing 12 · Kaminkäse 12 ·
  Frucht-Balsamico-Dressing 11 · Kräutermix „Grüne Soße“ 11 · Gelbe Beete 10 · vegane Knusperstäbchen nach Fisch Art
  10 *done* · Cheesecake Rote Früchte 9 · nach Geschmack Salz 9 *done, parser* · Pikantes Zitrus-Dressing 8 ·
  Porridge exotische Früchte 8 · Mini-Pancakes 8 · Mediterraner Mix 8 · Hartkäse ital. Art – DONT USE 8 *done,
  override* · Sonntagsknödel 8 · Gratis Veltins Bier 7 (promo line, left for Nate) · Antipasti-Trio 7 · veganes
  Schnitzel 7 *done* · Kebab Paste 7 · Bio-Müslikugeln DIY 7 · Bio-Mürbteig Backmischung 7 · Torchietti 7 ·
  Grillbrot mit Sunny Rub 6 · Grillhaxe 6 *done, reuses 267* · Rahmjoghurt 6 · Pastrami mit Pfefferrand 6 *done* ·
  Frühstücksherzen 6 · Lemon Cheesecake 6 · Hackfleischzubereitung aus Hähnchen 6 *done* · Schokoladiger Lava Cake 6
  · Nuss-Frucht-Mix 6 · Weinblätter gefüllt mit Reis 6 · Krustenbraten 6 *done, reuses 263* · dunkle Weintrauben 6 ·
  Crunchy Bites Smokey BBQ 5 · Schokolade 70% 5 · Geflügelwürste 5 *done* · Frühstücksherzen Mehrkorn 5 ·
  Nuss-Fruchtmix Schokolade 5 · vegane Kräutercrème 5 · SalaRico® 5 · bunte Gemüsewürfel 5 · Braune Soße 5 ·
  Krakauer 5 *done, possible animals* · Olivia-Aufstrich 5 · Weißer Reis, vorgekocht 5 · Boemboe Rendang 5 ·
  veganes Gyros 5 *done*. The full list is `rematch_<stamp>_noitem.tsv` on the server.
  Decided with the follow-up: `pflanzliche Margarine` (157 lines) → 377 margarine. Still open: the review TSV groups
  that are wrong `like` matches (chocolate desserts, Karotten Buns, Schoko-Ostereier, Skipjack Thunfisch in
  Sonnenblumenöl, Nduja Ravioli: they stay in review, never applied), `Balsamicoessig, hell` (→ 1053?), `Joghurt`
  (several yogurts), `gehackter Knoblauch & Ingwer in Öl` and similar paste lines, `Honig-Senf-Dressing`,
  `Ziegenfrischkäse-Crumble mit Honig`, `eingelegter grüner Pfeffer`.
  Meat lines (migration 0013, in #80): Hirschsteak 1822 (Wild) and Schweinsbraten-Aufschnitt 1823 (Schwein) are
  new; Rinderkeulenstreifen reuses 1660 Rindergeschnetzeltes (Rind).
- **Brand list** — `later`. Brand recognition only knows single-word brands, so aggregator
  offers like "Johnnie Walker Black Label" get "Ohne Marke". Curated multi-word brand list;
  clean up invented brand rows ("Fischstäbchen", "Deutsche", "BIO", fragments) and spelling
  duplicates (KING'S CROWN / KING’S CROWN, LANBECK / LANDBECK, …).
- **Aggregator per-offer end dates** — `idea`. The page header gives the week (Mon–Sun); exact
  "bis …" dates are only on each offer's own page. Fetch only offers not seen before.
- **Seed sync** — `later`. 9 live inventory items are missing from `seed/inventory_catalog.sql`
  (ids 129, 1034, 1179, 1216, 1218, 1431, 1432, 1433, 1505); export them into the seed.

## Receipts and purchases

- **Receipts → purchase history** — `in progress`. 22 Penny receipts (June–Oct 2026, PDFs) parse to
  the cent (#64); read-only analysis: every line parsed. Receipts get their OWN
  tables (`purchase_receipt`, `purchase_receipt_line`, `purchase_receipt_adjustment`, migration 0008) —
  NOT `purchase_history`, which doesn't fit (store_item / expiration date NOT NULL, INTEGER quantity, no
  receipt link). Every line is stored, matched or not; coupons are adjustments on the product before
  them. `ReceiptImportRunner` (dry run, `--commit` = one transaction, duplicates by store + date-time +
  receipt number) never calls `PurchaseService` and never changes stock: the household's stock is built
  from new purchases and check-ins, not reconstructed from old receipts. First dry run on Nate's 22 receipts
  (2026-10-13): all NEW and balancing. Fixed before the real `--commit`: PENNY group coupons
  ("App-Coupon 10% Waren", one per tax class, printed after an unrelated product or as a block at the end)
  are stored without a line instead of on the product before them; a discount that would make a paid total
  negative is stored without a line and reported. New items 1818–1821 (migration 0009) and override
  changes per Nate's decisions. Next (on the server, Nate): snapshot → apply 0009 → rebuild → dry run → `--commit`.
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

## Store offers: location, sources, flyer extraction

Research from Nate's design-patterns session, 2026-10-23. Nothing here is built. Licence readings are
not legal advice; verify before any public release.

- **`OfferSource` interface + factory** — `in progress`, this PR. `OfferSource` (`fetchOffers(OfferScope)`),
  `AldiNordSource` / `PennySource` / `AggregatorSource` and `OfferSourceFactory` (keys `aldi`, `penny`,
  `aggregator:<chain>`) are in; `StoreImportService.importOffers(source, scope)` is the one shared flow and
  the three old methods delegate to it, so behaviour and `RefreshServiceTest` are unchanged. Simple Factory
  is enough; Abstract Factory would be overkill. Still to do: `RefreshService.refreshWeekly()` and
  `StoreImportRunner` still list the chains by hand (`RefreshServiceTest` mocks the three old methods, so
  switching them to `importOffers(key)` means rewriting those mocks); an unknown aggregator key now throws
  `IllegalArgumentException` (was a NullPointerException). Not run locally: the Docker-based tests.
- **Offers scoped by household location** — `idea`. Design agreed in the session: postal code + radius →
  store discovery → refresh → homepage prioritises favourite retailers → "all stores" view lists every
  supported retailer in the area. Favourites only sort, never filter ("surface, don't decide").
  Tables (same MariaDB, package `de.dahaem.locations`; a separate database is not worth it at this size,
  revisit for data-residency rules, regional hosting or other consumers of the location data):
  `store_branch`, `household_location`, `household_favorite_chain`, and a `scope_key` on `store_offer`.
  Offers belong to a price region, not a branch (Penny market id, PLZ for famila / Edeka / Rewe, national
  for Lidl), so refresh once per distinct `(chain, scope_key)` that at least one household's radius
  includes, never per user. Build in from the start for Austria / Switzerland: `country_code` on branch,
  location and scope, postal codes as strings keyed by `(country, postal_code)`, currency per offer, chains
  per country. Open: Aldi Süd has no importer (the `Aldi` row is Nord only).
- **Store locations: OpenStreetMap** — `idea`. Bulk-load branches into `store_branch` (Overpass per chain,
  or a Geofabrik Germany extract), monthly refresh. ODbL allows storing and reuse; public use needs the
  credit "© OpenStreetMap contributors" with a link to the licence; a public extraction counts as a
  derivative database (keep it a plain OSM table, keep household data in separate tables). Do not use the
  public Nominatim per user (1 request/s cap, no heavy use). Google Places forbids storing anything but
  place ids; Gelbe Seiten has no API found. PLZ → coordinates: GeoNames postal codes (CC BY, attribution;
  the two readmes name CC BY 3.0 and 4.0, check); OpenPLZ code is AGPL-3.0 but its data licence and upstream
  sources are unverified; Austria's RTR PLZ directory (data.gv.at) licence unconfirmed. Probe of 10 km
  around Rotenburg (Wümme) returned 72 shop rows, ~57 distinct entries: ~20 chains worth tracking (Aldi Nord,
  Penny, Netto, Lidl, Edeka, Rewe, famila, Combi, Mix Markt, Hol'ab, Rossmann, TEDi, Jawoll, Woolworth, JYSK,
  OBI, Das Futterhaus, plus fashion chains); the rest are independents with no flyer. Curate a "supported
  chains" list; discovery lists all branches. Open: decide the manual "my store is missing" path.
- **Offer sources: what is reachable** — `next`, Nate decides scope. Tested 2026-10-23, one request each:
  Edeka `edeka.de/eh/service/eh/offers` → 403 Akamai; Rewe national offers page → 403 bot-challenge page
  (both left alone, bot protection is not bypassed); Lidl leaflet JSON
  `endpoints.leaflets.schwarz/v4/flyer?flyer_identifier=<slug>&region_id=0` works (slug from
  `lidl.de/c/online-prospekte/s10005610`) but its 158 products are all non-food, grocery offers exist only as
  page images. Netto sits behind Akamai (per a third-party comparison, untested). Aldi Nord and Penny stay the
  direct sources. aktionspreis.de terms (AGB, 2015 copy) allow private use only and ban commercial reuse
  ("strengstens untersagt", 3.6), so the aggregator is private-use only; a public product needs written
  consent or another source. kaufDA / Bonial, marktguru and Shopfully list no public API or data licence;
  Bonial's CEO says data flows from retailers to Bonial, not out. No price for a licence found. Apify
  scrapers (about $2 per 1,000 offers) read the same public pages, say "not a paid data license" and add
  no technical gain over our own clients; not worth using. Open: send a short inquiry to Bonial, marktguru
  and Shopfully; decide private household tool vs public product (decides whether licensing blocks).
- **Flyer images → structured offers with a vision model** — `idea`. Lidl's grocery offers exist only as flyer
  page images (`pages[].image` in the leaflet JSON, 73 pages for 19–24.10.2026). Reading them with a model
  works, no custom training needed (archive.org's old flyer scans have no labels and are the wrong era,
  useful at most as an evaluation set). Full-flyer run, 3 chunks per model, Opus 5.5 as reference:
  offers found Opus 423 / Sonnet 5.5 423 / Haiku 5.5 426; core price fields (price, old price, Lidl Plus
  price, percentages, deposit) agree with Opus 99.6% (Sonnet) and 99.1% (Haiku), all fields 97.6% / 97.5%.
  Tokens (agent totals, no input/output split, include tool overhead): Opus 339,369, Sonnet 307,605,
  Haiku 460,930; estimated cost per flyer at 80% input / 20% output without caching about $2.40 / $1.10 /
  $0.08 (Opus $4 / $20, Sonnet $2 / $10, Haiku $0.10 / $0.50 per MTok in / out; Batch API halves it).
  Cost scales with chains and regions, not users, since a flyer is processed once. Opus is not ground
  truth: it dropped multi-buy ("3 für 2": dealQuantity 3 + total price, Haiku was right) and left many
  printed per-kg prices empty; Sonnet merged size variants into one offer (Haiku and Opus split them, which
  fits the item rule). Most remaining differences are schema ambiguities, not misread digits. Before
  building: define rules for Lidl Plus-only prices (is the crossed "Normalpreis" `price` or `normalPrice`),
  per-kg lines covering several pack sizes, per-offer validity (Aktion vs Lidl Plus windows, banners like
  "Ab So. 11.10."), and add `dealQuantity` / `dealPrice` / `loyaltyPrice` / `loyaltyBasePrice` to the
  extraction schema; add a validation pass (sale price below old price, range per category) and keep the page
  link per offer. Proposed pipeline: Haiku 5.5 first pass, re-run pages that fail validation on Sonnet.
  Open: hand-label 30–50 offers for a measured accuracy (the labels here were model-made, 3 of 73 pages and
  about 10 disagreements were checked by eye); copyright and text-and-data-mining rights for Lidl's flyer
  images (German TDM exception applies only if the rightsholder has not reserved it machine-readably).

## API work for the frontend (from the UX spec)

Gaps found 2026-10-13 by reading the current API and schema; agents build these, Nate builds the pages.

- **API audit for the UI** — `in progress`, done in this PR (2026-10-23): `docs/api-audit-ui.md` has a table per
  page (10 pages, 87 needed calls: 14 covered, 21 partly, 52 missing) and 19 open questions for Nate. Every gap is
  an item in this section. Correction to the earlier note: a recipe CAN be removed from a plan
  (`DELETE /api/meal-plans/{id}/recipes/{recipeId}`); what is missing is editing / moving an entry and reading
  one week. Move to Done when merged.
- **Meal plan: any day, any number of meals** — `next`. `meal_plan_recipe` has no date, its key
  (recipe, plan) blocks the same recipe twice in a week, and `meal_time` is a fixed slot text. Needs
  an entry id, a date, an order within the day, servings per entry and an optional free label (no
  breakfast / lunch / dinner slots); endpoints for one week by date (today only `GET /api/meal-plans`, all weeks), add / edit / remove / move
  (`mealTime` is a required non-blank text on add; DELETE addresses the recipe, not an entry). Audit adds: each
  planned entry carries its recipe thumbnail and servings so Home and the planner need no call per meal.
  Migration with a data move for existing plans → snapshot first, guarded.
- **Recipe search and paging** — `next`. `GET /api/recipes` returns all ~11,000 recipes. Server-side
  search (name, ingredient), filters on data that actually exists (time, tags, servings), paging, and a
  small card DTO (drop the `diet` block from cards unless rules are sent); lists of tags, cuisines and diet
  presets so the filter controls can be built from data; the planner's recipe picker uses the same endpoint. Tags are ready to filter on:
  each recipe in the list carries its tag names and `recipe_tag` has an index on the tag id. The diet
  filter (`RecipeDietFilter` / `DietFilterService`, package `de.dahaem.diet`) takes rules and returns a
  verdict per recipe id, so search and paging can reuse it; today it loads the facts of every recipe per
  request (a few hundred ms) — cache or push the tag part into SQL when paging arrives.
- **Recipe images** — `in progress`, this PR (migration 0010, `enrich_recipes.py`). Images are LINKED, never
  copied: `recipe_image_url` / `recipe_thumbnail_url` hold the URLs and the browser loads them from
  HelloFresh; `recipe_image_source` (`hellofresh` | `own` | `generated`) lets all of them be replaced in
  bulk later (bought licence, own photos or AI-generated). The PHP shows a designed fallback when there is
  none. Next (on the server, Nate): snapshot → apply 0010 → rebuild `dahaem-api` → `enrich_recipes.py` → review →
  apply the SQL in one transaction. Same PR adds description, cuisine (German, `config/cuisine_map.json`),
  rating and publish date.
- **1,294 cached recipes never imported** — `later`. The page cache holds 12,162 pages; 10,868 tie to a DB
  recipe, 1,294 have none (read-only check 2026-10-13). Decide whether to import them (extract + load) or
  drop them from the cache; `enrich_recipes.py` only counts them.
- **Allergens** — `idea`. Needs allergen data per item (the pages hold no allergens, diet flags or tags);
  nothing to show or filter on until the catalogue carries it. Idea: per catalogue item like
  `inventory_item_animal` (gluten, milk, egg, nuts, fish, soy …), derived to recipes the same way,
  shown as warnings; needs Nate's allergen list and a rule for "may contain". The certain / possible split
  and the diet filter (`diet`, `excludeTags`) are the model to reuse.
- **Ambiguous items in shopping and offers** — `later`. The shopping list and the offers still treat "Salami"
  as one item. A household without pork should see only beef / poultry salami offers: needs the possible
  animals of generic items on the product side (store products and offers), the same surface-don't-decide
  hints as for recipes, never an automatic pick.
- **Kosher fish (fins and scales)** — `idea`. The Tier tag is just "Fisch"; fish without fins and scales
  (Aal, Wels, Stör, Hai …) are not kosher but cannot be told apart yet. Needs a fish-species value (or a
  flag) per item and a rule in the koscher preset; until then the preset's note says so.
- **Recipe opportunities with evidence** — `next`. `/api/suggestions/by-savings` and `/by-expiring`
  exist; extend them so each suggestion says which ingredients are covered by which offer (store,
  product, price, valid until), which are already in stock and which are still uncovered. Suggestions
  never change the plan by themselves. Today a suggestion is only `recipeId`, name, score and matching item ids;
  by-savings reads the curated `store_discount` only, not the flyer `store_offer` rows; nothing suggests from
  low stock. Add the recipe image so Home needs no extra call.
- **Shopping list lifecycle** — `next`. Save a list (from a meal-plan week), tick items, record the
  shop as purchases (ties in with the receipt import and "Receipts + shopping list"); on refresh warn
  when a chosen offer has expired or a price changed, never substitute silently. Audit adds: list the lists (open
  list of a week / household), add a manual line (`is_manual` column exists), change quantity, remove a line,
  tick a line (`purchase_status` exists, never written), complete a list (`status` exists, never set), keep a
  price snapshot per selection so "price changed" can be told. Problems in the current save (see
  `docs/api-audit-ui.md` "Possible bugs"), to be fixed with this item: items nobody sells are silently dropped
  (the spec wants them shown as unavailable); the saved line has no unit and `quantityNeeded` is an integer, so
  fractional amounts are rounded up.
- **Inventory writes** — `next`. Add, consume, correct and expiry changes with a history, so low /
  expiring items can lead to "adjust stock", "plan a recipe" or "add to the list". Audit adds: today only a
  purchase changes stock; `GET /api/inventory` returns the whole catalogue incl. zero stock with no search,
  category filter or paging; expiry is one date per item (batches are an open question); low-stock has no
  recipe suggestion. Open: threshold is whole units regardless of unit.
- **Auth and session review** — `next`. Check PHP sessions against the Java login / me / CSRF
  endpoints together: session expiry, logout, errors that reveal nothing internal. Account creation /
  invitation is open (spec decision 6); no public self-registration until it is decided. Audit finding (the main blocker): with `X-Internal-Token` the
  principal is the fixed name `internal-api`, so `GET /api/auth/me` answers 401 for the PHP frontend and no
  endpoint knows which user or household is asking. Needs a decision how PHP passes the acting user (trusted
  header next to the token, or a credentials-check call that returns a user id) and a "verify credentials"
  endpoint that does not open a Java session. Blocks "Household isolation" and every per-user setting.
- **Stores and pinned stores** — `later`. A store list with name and logo for the Home offers deck and
  "offers of one store"; per-user pinned stores change only the order, never hide stores or offers.
  Store logos / banners: check the usage rights, fall back to a neutral DAHÄM card. Today `store` is id + name
  only and no endpoint lists stores.
- **Personal vs household settings** — `later`, needs Nate's decision (spec decision 7). Greeting,
  language, theme per user; plan, stock, list and purchases shared per household. Audit adds the endpoints the
  profile page needs: read / change own profile and password, list household members, invite / remove, roles
  (tables exist, only `/me` reads them).
- **Food questionnaire** — `next`, PHP / Nate. A page per person: "Was esse ich nicht?" (hard rule) and "Was esse
  ich lieber nicht?" (soft rule), on tags (Schwein, Alkohol, vegetarisch …, or a preset) AND on single
  ingredients (Pilze, Koriander). The API side is ready: the PHP passes the answers as `diet`,
  `excludeTags`, `excludeItems` (hard) and `avoidTags`, `avoidItems` (soft) and `includeUnclear`. Open: where
  the answers are stored (see "Personal vs household settings") and per person vs household.
  Recommendation: the hard rules of EVERY member apply to the shared meal plan, soft rules only mark.
- **Offers read API** — `next`. The Offers page and the Home store deck have nothing to read: `store_offer`
  (flyer rows with price, deal, deposit, valid from / until, region, source) and `store_offer_price_change` are filled
  by the importers but no endpoint exposes them. Needed: current offers of one store, search / category / paging,
  offer detail, and the match to a generic item with uncertain matches shown as uncertain. Open for Nate: flyer
  `store_offer` vs curated `store_discount`; whether the store region is chosen per user or per household.
- **Purchases and receipts for the UI** — `later`. `GET /api/purchases` returns the whole history with ids only
  (no product, store or item name), no paging or date filter; spending is one total with no breakdown by store,
  category or month; receipt import is a command-line runner only, so the review-and-confirm flow (spec journey D)
  has no endpoint (upload or pass text, list matches, confirm / correct, then purchases + inventory), and the
  price history (`store_price_history`) is not exposed.
- **Meal-plan endpoints scale with history** — `later`. The add, remove and shopping-list handlers load every plan
  to find one id and `GET /api/meal-plans` runs one query per plan. Harmless today; replaced naturally by "one week
  by date" in the meal-plan item. Found in the API audit, not fixed.
- **Unit check when stock is subtracted from a list** — `idea`, verify first (read-only). `persistGeneratedList`
  computes `missing = totalQuantity - inStock` with `Item.getQuantity` in the item's own unit. Check whether a
  recipe unit and a stock unit can differ for the same item; if yes it is a bug, if not, document the guarantee.
  Found in the API audit.
- **Open questions from the API audit** — needs Nate. 19 questions (acting user, week definition, repeated
  recipes in a plan, servings level, one or several open lists, what completing a list does, unavailable lines,
  offers source and region, logos, inventory batches, receipt upload …) are listed at the end of
  `docs/api-audit-ui.md`; answer them one at a time (spec rule), the matching items above wait on them.

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

- Catalogue batch 2b: migration 0015 (67 new items, ids 1833–1899, every no-item name with ≥ 5 lines plus Nate's
  varieties and products), the tightened item rule in CLAUDE.md / `docs/DATABASE.md`, the amount rules of the rematch
  and the importer (kept amounts incl. salt, EL/TL/ml/dl for liquids, pinch dropped next to a spoon, alcohol to taste,
  dl / mg), overrides, `non_food.json` (Ausstechformen, Gratis Veltins Bier), `docs/variant_split_review.md`
  (report only). Nothing applied live; PR number added when merged.

- Cleanup: the old SQLite catalogue tools (`add_ingredients.py`, `dedup_catalog.py`, `scan_ingredients.py`), the
  console runners `Main` / `ShoppingListDemo` and the SQLite-era `docs/CODE_REVIEW.md` are removed; stale SQLite
  wording fixed in the README, docs and test comments.

- Rematch of the unmatched recipe lines, catalogue items 1822 / 1823, the canned-dish corrections (877 chili sin
  carne → canned legumes, 887 gemüsetopf → canned vegetables), the currywurst-sauce tagger fix and the EU
  proteinreich rule: migration 0013, `rematch_unmatched.py` (follow-up to #79; PR number added when merged).

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
