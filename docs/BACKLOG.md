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
- **Stock and fond as catalogue items: one per type, quantity in litres** — `idea`, Nate 2026-11-03. Each type is its
  own generic item (Gemüsebrühe, Rinderbrühe, Geflügelbrühe, and each fond: Kalbsfond, Fischfond …; fond ≠ Brühe).
  The form is not a separate item: powder, cube (Würfel) and glass / paste of one type are the SAME item. Their
  quantity is classified by how many litres of finished stock one pack, glass or cube makes (so a recipe's
  "500 ml Gemüsebrühe" and a "Stück" cube can be compared in litres); needs a litres-per-pack value per store
  product, and ties in with the "Conversion table" below. The Brühe family stays for matching and for substitutions
  later (a household may swap one type for another), never for item identity. Today the catalogue has the plain item
  1851 `Brühe` (unit g) from batch 2b and item 780 `Gemüsebrühe Würfel` (the form is in the item name: to rename).
- **Brühe rule gaps** — `idea`. Two gaps left by #91, not built: a Hähnchenbrühe step does not fill a Geflügelbrühe
  line (no alias rule; Nate decides whether Hähnchen counts as Geflügel), and a typed step that matches no stock line
  leaves that line "to taste" instead of putting it in the review. Source: review of #91.
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
- **Diet filter: what a household does not eat** — `in progress`, merged in #79 / #80 (migrations 0012 / 0013); the
  live steps below are Nate's. New Tier values
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

- **Receipts → purchase history** — `in progress`, merged in #73 / #76. 22 Penny receipts (June–Oct 2026, PDFs) parse to
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

- **`importOffers(key)` in the refresh jobs** — `next`. Left over from #88: `RefreshService.refreshWeekly()` and
  `StoreImportRunner` still list the chains by hand. Switching them to `StoreImportService.importOffers(key)` (a loop
  over `OfferSourceFactory.keys()`) means rewriting the mocks in `RefreshServiceTest`, which mocks the three old
  methods. Small, Java only, no schema change.
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
- **Offer sources: what is reachable** — `next`. Tested 2026-10-23, one request each:
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
  no technical gain over our own clients; not worth using. **Decided 2026-11-03 (Nate): private use for now, open
  sources only.** Parse what is publicly reachable: Aldi Nord and Penny directly, the aggregator (private use only,
  see its terms) and flyer images read by a model (next entry); Lidl, Edeka, Rewe and Netto stay out until a way in
  exists. No inquiry to Bonial, marktguru or Shopfully and no company API or licence work until the MVP runs and Nate
  is happy with the product; then a private API for the sources.
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
  images (German TDM exception applies only if the rightsholder has not reserved it machine-readably). Private use
  for now (Nate, 2026-11-03), so the licence question waits for the MVP like the other sources.

## API work for the frontend (from the UX spec)

Gaps found 2026-10-13 by reading the current API and schema; agents build these, Nate builds the pages.

- **Meal plan: any day, any number of meals** — `in progress`. **Design decided 2026-11-04 (Nate), schema in
  migration 0016 (applied live by Nate on 2026-11-04, verified 2026-11-06):** `meal_plan_recipe` is changed in place, no archive table. Its own
  `meal_plan_recipe_entry_id` is the primary key (the same recipe can be planned many times, each meal is addressed by
  its id); the recipe and plan columns stay foreign keys; new columns: date of the meal, servings (nullable), position
  within the day, note; the existing `meal_plan_recipe_meal_time` becomes the free label. A plan stays one
  Monday-to-Sunday week. Servings of a meal, most specific first: its own number, then `recipe.recipe_preferred_servings`
  ("this meal is always for X"), then `household.household_people`, then 2; meals that have not happened yet follow
  the household number, a hand-set number stays. Opening a week with no plan returns seven empty days, adding the
  first meal creates the plan. The old endpoints stay until the PHP has moved (a meal added the old way lands on the
  week's Monday with its text as the label). **The Java API for it is complete after PR C and live since 2026-11-06 (see "Live deployment state"); what is left
  is the PHP page.**
  **Built in PR C:** meals of a past day (household time zone, `dahaem.mealplan.zone`) cannot be added, edited, moved
  or removed (409), and a nightly job at 00:01 (`ScheduledMealServings`) writes the resolved servings onto every past
  meal that still followed the default, catching up on missed nights; it uses the head count of the first (shared)
  household, because plans have no household column yet, and the first run also freezes the old rows of the live
  database. The old endpoints do not check the day and stay until the PHP has moved. **Built in PR B:** the shopping list from a plan scales each meal by its own resolved servings
  (`ShoppingListService.generateFromMealPlanByServings`; a recipe planned twice counts twice; `?people=N` still
  overrides; optional `?householdId=`). **Built:**
  reading and setting the household head count (`GET /api/households/{id}`, `PUT /api/households/{id}/people`); the
  PHP decides who may call it, because the API cannot tell which account is acting yet (see "Auth and session
  review"). **Built in PR A (meal planner by date):** one week by date (`GET /api/meal-plans/week`), plan / edit /
  move / remove a meal by its entry id (`POST` / `PATCH` / `DELETE /api/meal-plans/meals`), servings resolution
  (`MealServings`: meal, recipe preference, household, 2), each planned meal with its recipe thumbnail, and the
  setter for `recipe_preferred_servings` (`PUT /api/recipes/{id}/preferred-servings`); contract in the README
  ("Meal planner by date"). The meal endpoints take an optional `householdId` so servings can follow the head count;
  until the auth question is settled the PHP chooses it. **Blocked:** the household column on plans (the user /
  household decision).
- **Retire `recipe_servings_max`** — `later`. Nate wants to drop the range column once ranges are unwanted (stored
  amounts are always for `recipe_servings`, 2 as the standard, and scaled in the logic). It is its own clean-up, not
  part of the meal plan: clear the ~77 values (the range is in the title), drop the CHECK
  `recipe_servings_max_above_servings`, remove `servingsMax` from the card and detail, `basis=max`, the servings
  filter of the search ("3–4 matches 3 and 4") and the importer's and repair tool's range handling (about 120 lines
  in about 20 code files, plus tests and docs). Breaking for the PHP contract. It would supersede "Servings ranges:
  importer, shopping list, household choice" and the portion-ranges idea. Decided 2026-11-04: not now, so the meal
  plan work does not depend on it; `recipe_preferred_servings` is a separate new column.
- **Recipe search: follow-ups** — `later`. Left over from #93: (a) accent- and typo-tolerant search (`gemuese` ≠
  `gemüse` today; `LIKE` on 11,000 rows is fast enough, a FULLTEXT index or a folded search column is the next step
  if the PHP needs it); (b) filters on data that exists but that Nate has not asked for yet: `cuisine`, calories /
  protein, rating, `servingsMax` basis, Nate decides which the page shows; (c) search by ingredient item id
  (`ingredientItems=`) for "use up what I have", needs the item picker first; (d) the diet snapshot is per process,
  so a listed verdict can be up to 60 s old after a tagger or importer run (the detail judges fresh), a data-version
  check would remove that; (e) the PHP recipe list and the planner's recipe picker move to `/api/recipes/search`,
  then the plain `GET /api/recipes` can be removed (Nate's PHP); (f) lists of tags, cuisines and diet presets so the
  filter controls can be built from data (API audit row 4.10).
- **Recipes without a time** — `next`, Nate 2026-11-03. Some recipes have no prep or cook time (stored as 0), so
  the time filter and the `time` sort cannot place them. Nate: with a dataset this large there is no need to work
  with incomplete data: give a time to every recipe that is otherwise complete, and for the rest either complete
  them or remove them from the databank. First, read-only: how many recipes have prep + cook = 0 and which of them
  are otherwise complete (the page cache may hold the times for some). Nate runs the count (2026-11-03). Removing recipes is a live-data change:
  snapshot first (keep at most 3), guarded SQL, Nate's approval; nothing is deleted before that. Until then the
  search hides them while a time filter is on.
- **Recipe images** — `in progress`, merged in #75 (migration 0010, `enrich_recipes.py`); the live steps below are
  Nate's. Images are LINKED, never
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
- **Recipe opportunities with evidence** — `in progress`. **Built 2026-11-10: `GET /api/suggestions/pantry`** (paged, with
  search, store, time and sort filters; ranking in `PantryRanking`, weights are constants to tune; the page then switches
  from `/by-savings` to it, dahaem-frontend). **Purpose, stated by Nate 2026-11-10:** healthy meals at the lowest
  shopping cost. "Healthy" means cooking from fresh ingredients; all recipe data counts as healthy by definition (it
  contains no ready-made or pre-processed meals), so no health filter is needed. **Ranking:** use up what is already
  in the pantry first, items closest to their expiry date weigh most (pantry and staple ingredients rank a recipe
  HIGHER, never lower), then the lowest extra cost: only the ingredients still missing have to be bought, and the
  offers on those are the savings. Each suggestion says which ingredients are in stock (and which expire soon), which
  are missing and which store has them on sale (store, pack, price, valid until), listed per store; the page never
  picks a store or a recipe. `/api/suggestions/by-savings` and `/by-expiring` exist but only count discounted
  ingredients (a suggestion is `recipeId`, name, score and matching item ids; by-savings reads the curated
  `store_discount` only, not the flyer `store_offer` rows) and ignore the inventory; nothing suggests from low stock.
  Needs a unit-aware "in stock" check first (see "Unit check when stock is subtracted from a list"). Add the recipe
  image so Home needs no extra call. Decided by Nate 2026-11-10: the cost
  stays as it is, the offers listed per store with their pack and price, no total per recipe; an estimated total cost
  comes later on the shopping list. **Review changes, same PR (Nate, 2026-11-10):** (1) the household's diet rules
  (`diet`, `excludeTags`, `excludeItems`, `avoidTags`, `avoidItems`, `includeUnclear`) apply to this endpoint exactly
  as in `/api/recipes/search` (hard rules hide and do not count a recipe, soft rules sort it after the others, each
  card carries `diet`), so the PHP only passes the household's rules on; (2) stock past its date is `EXPIRED`: shown
  with its amount and date, not counted as usable stock (the ingredient counts as still to buy and carries the offers on
  sale), and never weighted as "use it up"; (3) the list holds at most 200 recipes, the best ones in the requested order
  (`total` <= 200, `totalMatching` = how many fit before the cap, `capped`), and more are found with
  `/api/recipes/search`. Still open: the "in stock" check only asks whether the household has any of the
  item, the amount is not compared with the amount the recipe needs (unit-aware check, see the item named above).
  An open product question: whether expired stock should rather count as usable (best-before dates are soft);
  it is one SQL condition and the status in `SuggestionRepository` if Nate wants it the other way.
- **Suggestions: limit, paging and filters** — `in progress`, replaced by `GET /api/suggestions/pantry` (see "Recipe
  opportunities with evidence"); `/by-savings` and `/by-expiring` stay unchanged until the PHP has moved. Found 2026-11-10 while building the Home page and the full
  suggestions page (dahaem-frontend PR 17). `GET /api/suggestions/by-savings` answers with every recipe that has at
  least one discounted ingredient: with the live data over 10,000 recipes, about 1.6 MB and 0.85 s per call, and the
  PHP loads it on every Home and suggestions page view. Today the PHP works around it: it filters and sorts the whole
  list itself (name words, at least n ingredients on sale with a default of 3, store, maximum minutes, sort by best
  match / name / time) and keeps the answer for 5 minutes in a temp file. The real fix is in the API: `limit` and
  `offset` (or `page` / `pageSize` with a `total`), `minScore`, `q`, `store`, `maxTime` and `sort`, applied in SQL like
  `GET /api/recipes/search`. Same for `/by-expiring`. The interim PHP ranking counts only
  discounted ingredients; the real ranking (pantry use first, then low extra cost) is in "Recipe opportunities with
  evidence" and replaces it, so build the paged call on that ranking. Also check the date: the ranking uses `LocalDate.now()` in the JVM time zone while the
  PHP uses Europe/Berlin; use the household zone (`dahaem.mealplan.zone`) so both agree around midnight.
  Builds on "Recipe opportunities with evidence" above it.
- **Offers per recipe ingredient in the API** — `next`. Found 2026-11-10 (dahaem-frontend PR 17). The recipe page
  marks the ingredients that are on sale with store, pack, price and last day, and Home / the suggestions page show
  the same as badges, but the API has no such call, so the PHP reads `store_discount` / `store_item` directly. Needed:
  current offers per ingredient on `GET /api/recipes/{id}` (or a batch call for several recipes): store, brand, pack
  size and unit (`store_item_package_size`, `store_item_package_unit`; a price without the pack misleads, e.g. 1,69 EUR
  is a 600 g pack and 4,44 EUR a 7.5 kg bag), sale price, regular price, valid until, and optionally the price per
  kg / l; and the picture, time, servings and rating on each suggestion so Home needs no extra read. Offers are listed
  by store, never ranked: the user chooses. The live data also holds identical offer rows (e.g. Butter at Aldi twice,
  same price and dates); the PHP now collapses them, but the importer or `store_discount` should not store them twice
  (unique key on store item, start and end date, price?). Decide with the "Offers read API" item.
- **Shopping list lines (PR D, migration 0017)** — `in progress` (migration not applied to the live DB by its PR; snapshot
  first). Built: a list has a shopping day (offers judged on it, each option with `validFrom` / `validUntil`) and a first
  meal day (`shoppingDate`, `from`; default today, the next day when shopping after dinner); every generation is a NEW
  list, never rewritten, and `GET /api/meal-plans/{id}/shopping-lists` lists them; lines keep a decimal amount and its
  unit, the recipes that need them, and stay when no store sells them (`available`); stock is subtracted only in the
  same unit and when not expired by the shopping day, otherwise nothing is subtracted and the line is `needsReview`
  (Nate reviews until the unit questions are settled). Still open: the estimated totals per store (next small PR), and
  the lifecycle below. The strict unit match sends g vs kg to review too; use `Units` to convert once the conversion
  table exists.
- **Shopping list lifecycle** — `next`. Nate 2026-11-10: the list shows an estimated total cost (per store); the
  suggestions deliberately do not. Save a list (from a meal-plan week), tick items, record the
  shop as purchases (ties in with the receipt import and "Receipts + shopping list"); on refresh warn
  when a chosen offer has expired or a price changed, never substitute silently. Audit adds: list the lists (open
  list of a week / household), add a manual line (`is_manual` column exists), change quantity, remove a line,
  tick a line (`purchase_status` exists, never written), complete a list (`status` exists, never set), keep a
  price snapshot per selection so "price changed" can be told. The save problems found in the API audit (items nobody
  sells dropped, no unit, integer amounts) are fixed in "Shopping list lines".
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

- **Live deployment state (checked 2026-11-06)** — `done` except the schema_migrations idea. **Second rebuild, same
  day (09:36 UTC):** snapshot `dahaem-20261106-093619.sql`, main e7b0e22 (#104) pulled, `dahaem-api` rebuilt alone
  (image 1780acc8b097, jar dated 2026-11-06 09:36), so the live API now has the meal planner by date (#101), the
  per-meal shopping list (#102) and the past-day rules with the nightly freeze (#104). db, web, phpmyadmin and both
  volumes untouched; GET smoke tests passed (`/api/meal-plans/week` with seven days, with and without `householdId`,
  `/api/households/1`, `/api/recipes/search`, `/api/recipes`, `/api/meal-plans`; PHP site 200). Snapshots kept:
  1022, 1106-0838, 1106-0936. Rollback images: `dahaem-api:rollback-20261106` (the #98 / #99 build) and
  `dahaem-api:rollback-20261022`; Nate removes them once the new build has proven stable. The nightly job runs at 00:01
  Europe/Berlin with the defaults (no `MEALPLAN_*` variable set); **its first run writes the resolved servings onto the
  3 legacy meal rows by design, so look for "meal servings freeze: N meal(s) of past days frozen" in the `dahaem-api`
  log after the first 00:01.** The household head count is still `null`: planned meals and the shopping list use the
  default 2 until Nate sets it (`PUT /api/households/1/people`), which he wants to do after the meal plan and shopping
  list are done. **Earlier update, same day:** the
  snapshot `dahaem-20261106-083838.sql` was taken, main (0d21dd7, #99) was pulled and `dahaem-api` rebuilt and
  restarted alone (new image 9ecbadb4bb8d, jar dated 2026-11-06; db, web, phpmyadmin and both volumes untouched);
  read-only smoke tests passed (`/api/recipes/search`: 11,070 recipes; `/api/households/1`; `/api/recipes`; the PHP site
  answers 200). The old image is kept as `dahaem-api:rollback-20261022`; Nate removes it once the new API has proven
  stable. The household head count is still `null` (set it with `PUT /api/households/1/people`). The store refresh is
  armed (`REFRESH_ENABLED=true`) with the code defaults, daily 06:00 and the 1st of the month 05:00 (see
  `docs/refresh.md`). The first read-only check follows; its points (1) and (2) are now done.
  Read-only check on llmbox against the live `sandbox`
  database: migrations 0001–0016 are all applied (objects and sample rows checked, not every row; the 0015
  animal-link rows were not checked one by one). There is no migration-tracking table, since migrations are run by hand
  in phpMyAdmin. Gaps 1713, 1715 and 1719 in the 0004 id range are gaps in `seed/inventory_catalog.sql` itself. Three
  things are open: (1) the `dahaem-api` container is out of date: created 2026-10-22, jar dated 2026-10-15, so it has
  none of #87–#98 (offer sources, recipe search, diet cache, household head count, the 0016 meal-plan code); the old API
  runs against the 0016 schema, which only adds columns and keys, so no migration is needed before a rebuild from main.
  Rebuild is Nate's decision. (2) The newest snapshot (`dahaem-20261022-084240.sql`) predates 0016; take a fresh one
  before the rebuild (keep ≤ 3, newest replaces oldest). (3) `schema_migrations` table — `idea`: record which migration
  ran and when, replacing this manual check; a schema change, so it needs Nate's approval and a snapshot first.

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

- **Home, suggestions and recipe page: first version built** — `in progress` (dahaem-frontend PR 17, 2026-11-10).
  Home shows the real week and 5 recipes that fit the offers; "Alle Vorschläge" opens a page with search, store,
  time and sort filters; the recipe page highlights ingredients on sale with the pack. Still open: the "in stock /
  still missing" badges (needs the stock-versus-ingredients comparison from "Recipe opportunities with evidence"),
  and `recipe.php?id=abc` answers 200 with the not-found view while a missing numeric id answers 404 (found in
  review; small fix in the PHP, not done).

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

- #93 Recipe search and paging: `GET /api/recipes/search` searches (`q`, `ingredient`), filters (`tags`,
  `minTime` / `maxTime`, `servings`), sorts (`name`, `time`, `rating`, `newest`) and pages (default 24, max 100, a bad
  value is a 400) on the server and answers `{items, total, page, pageSize, totalPages}` with small cards.
  `GET /api/recipes` is unchanged (the plain array) until the PHP has moved over. The diet rules page too, from a
  60 s snapshot in `DietFilterService`. Decided by Nate 2026-11-03; contract in the README. Open: "Recipe search:
  follow-ups", "Recipes without a time".
- #92 / #95 API audit for the UI: `docs/api-audit-ui.md`, a table per page (10 pages, 87 needed calls: 17 covered,
  18 partly, 52 missing after the recount of 2026-11-03) and 19 open questions for Nate; every gap is an item in "API
  work for the frontend". Correction to the earlier note: a recipe CAN be removed from a plan
  (`DELETE /api/meal-plans/{id}/recipes/{recipeId}`); what is missing is editing / moving an entry and reading one
  week.
- #91 "Brühe" family rule: a step's bare `Brühe` fills the recipe's only stock line; a step naming a type (Hähnchen-,
  Rinder-, Gemüsebrühe ...) fills only a line of that type; Fond is its own family and never matches Brühe (item rule;
  Nate, 2026-11-03). `scripts/importer/step_amounts.py`, 185 importer tests. Open: "Brühe rule gaps".
- #89 CI approval gate for stale generated docs: `docs-diff` → `docs-update` (waits for an approval in the
  `docs-approval` environment, then commits the regenerated docs to the PR branch) → `docs`. Tested end to end on
  throwaway PRs (#90, #94) and on #93. GitHub asks for one more "Approve and run" click on the bot's commit.
- #88 `OfferSource` interface and factory: `OfferSource`, `AldiNordSource` / `PennySource` / `AggregatorSource`,
  `OfferSourceFactory` (keys `aldi`, `penny`, `aggregator:<chain>`); `StoreImportService.importOffers(source, scope)`
  is the one shared flow and the old per-chain methods delegate to it. An unknown aggregator key is now an
  `IllegalArgumentException` (was a NullPointerException). Open: "`importOffers(key)` in the refresh jobs".
- #87 Store offers research (location, sources, flyer extraction) recorded in this file, "Store offers" section.
- #86 Catalogue batch 2b: migration 0015 (67 new items, ids 1833–1899, every no-item name with ≥ 5 lines plus Nate's
  varieties and products), the tightened item rule in CLAUDE.md / `docs/DATABASE.md`, the amount rules of the rematch
  and the importer (kept amounts incl. salt, EL/TL/ml/dl for liquids, pinch dropped next to a spoon, alcohol to taste,
  dl / mg), overrides, `non_food.json` (Ausstechformen, Gratis Veltins Bier), `docs/variant_split_review.md`
  (report only). As of the PR nothing was applied live.

- #85 Cleanup: the old SQLite catalogue tools (`add_ingredients.py`, `dedup_catalog.py`, `scan_ingredients.py`), the
  console runners `Main` / `ShoppingListDemo` and the SQLite-era `docs/CODE_REVIEW.md` are removed; stale SQLite
  wording fixed in the README, docs and test comments.

- #84 Rematch follow-up: matching fixes, catalogue batch 2a (migration 0014).
- #80 Rematch of the unmatched recipe lines, catalogue items 1822 / 1823, the canned-dish corrections (877 chili sin
  carne → canned legumes, 887 gemüsetopf → canned vegetables), the currywurst-sauce tagger fix and the EU
  proteinreich rule: migration 0013, `rematch_unmatched.py` (follow-up to #79).

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
