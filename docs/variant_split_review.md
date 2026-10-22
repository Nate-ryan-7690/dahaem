# Variant split review

Nate's item rule of 2026-10-22 (CLAUDE.md "Codebase conventions", `docs/DATABASE.md`): if it is its own product on the store
shelf, it is its own `inventory_item` — varieties and flavours included; only the brand is abstracted away. Earlier work
(PRs up to #85: migrations 0004–0014, `config/store_overrides.json`, `config/receipt_overrides.json`, the receipt rules)
followed the **old** rule ("flavour variants stay one item"). This document lists where that merged varieties, with the
live rows affected and a proposed split. **Nothing here was changed**: no data, no catalogue item, no override. Nate decides
each row; accepted rows become a catalogue migration plus override changes plus a guarded re-match of the affected rows.

Generated 2026-10-22 from the live database (read-only) and the two override files. How to read the counts:

| Column | Meaning |
|---|---|
| keys | override keys that map to the item today: `config/store_overrides.json` / `config/receipt_overrides.json` (they act on the NEXT pull / import) |
| live rows | rows that are matched to the item NOW: store products / their store offers / receipt lines |


Live totals for context: 375 store products are linked to an item, 5207 store offers exist, 185 receipt lines are matched (30 receipt keys and 399 store keys in the override files).


## 1. Catalogue items and override keys that merge varieties

| item | what is merged | keys (store / receipt) | live rows (products / offers / receipt lines) | proposed split |
|---|---|---|---|---|
| 1677 Tiefkühlpizza | all frozen-pizza types: Ristorante Salame / Pepperoni-Salame / Spinaci / Tonno, Wagner Diavolo / Salami / Piccolinis (incl. a Sonic hot-dog-style mini pizza), Trader Joe's stuffed crust / fully loaded, Cucina Neapolitan style, La Mia Grande / Familia, Tradizionale, Suprema, Steinofen, Pizzeria style | 23 / 0 | 1 / 0 / 0 | one item per topping / type: Pizza Salami, Pizza Pepperoni-Salami, Pizza Spinat, Pizza Thunfisch, Pizza Diavolo, Mini-Pizza (Piccolinis), Steinofenpizza (plain), Pizza mit gefülltem Rand; the sizes (Grande / Familia) are not varieties |
| 1687 Eiscreme (Packung) | all ice-cream tubs: Ben & Jerry's, Cremissimo, Magnum Eisbecher, Nutella-Eis, soft ice cream | 7 / 0 | 1 / 0 / 0 | one item per flavour as printed on the pack (Vanille, Schokolade, Cookie Dough, ...); needs the flavour per product from the store data |
| 1689 Konfitüre | all jams: the receipt override maps 'Landliebe Konfit' (flavour not on the receipt) and the store key 'konfituere' | 1 / 1 | 1 / 0 / 2 | one item per fruit (Erdbeer, Aprikose, Pflaume, Johannisbeer, ...); Kirsch is the new 1837 Kirschkonfitüre from this batch; a receipt that does not name the fruit stays on 1689 or goes to review |
| 1696 Helles / Lager | 'Helles / Lager' = Augustiner Hell, Hofbräu, Veltins Pülleken (a Pils!) with Heineken, Budweiser, San Miguel, Corona | 18 / 0 | 0 / 0 / 0 | split Helles and Lager (Corona / Budweiser / Heineken are Lager); Veltins Pülleken belongs to 1695 Pils |
| 1714 Kräuterlikör / Bitter | 'Kräuterlikör / Bitter' = Ramazzotti Amaro, Fernet Branca, Hubertus Tropfen, Underberg | 5 / 0 | 0 / 0 / 0 | Amaro (Ramazzotti), Fernet, Magenbitter (Underberg), Kräuterlikör (Hubertus) |
| 1716 Mixgetränk (alkoholisch) | 'Mixgetränk (alkoholisch)' = Jack Daniel's & Cola, Jim Beam & Cola, Jack Daniel's Berry | 6 / 0 | 0 / 0 / 0 | Whiskey-Cola (alkoholisch) and Whiskey-Beere (alkoholisch), as separate items |
| 1717 Kaffeebohnen | coffee beans: Lavazza Espresso / Crema, Dallmayr Home Barista Dolce / Forte, Melitta Bella Crema, Segafredo | 12 / 0 | 0 / 0 / 0 | Espresso-Bohnen, Café-Crema-Bohnen, Barista-Bohnen (mild / kräftig) |
| 1720 Kaffeegetränk (Fertig) | ready coffee drinks: Starbucks Eiskaffee, White Chocolate Mocha | 4 / 0 | 0 / 0 / 0 | Eiskaffee (fertig) and Mocha (fertig) |
| 1721 Colorwaschmittel | scented colour detergents: Lenor Color Amethyst Blütentraum (liquid + powder), Persil Color powder | 4 / 0 | 0 / 0 / 0 | liquid and powder separate (they are different products); scent variants only if you want them tracked |
| 1727 Waffelschnitte | 'Waffelschnitte' = Kinder Bueno, Kinder Country, Kinder Maxi King, Knoppers, Hanuta | 10 / 0 | 0 / 0 / 0 | Haselnuss-Waffelschnitte (Knoppers / Hanuta), Kinder Bueno, Kinder Maxi King, Kinder Country: different products on the shelf |
| 1729 Fruchtjoghurt | 'Fruchtjoghurt' = Landliebe Joghurt auf Frucht, Müller Joghurt mit der Ecke, Ehrmann Almighurt, Zott | 5 / 0 | 0 / 0 / 0 | per fruit flavour; Erdbeer 390, Kirsch 392, Heidelbeer 393, Pfirsich 394 and Mango 395 yoghurts already exist, so the flavour keys can map to those |
| 1730 Pudding | puddings: Ehrmann Grand Dessert (all flavours) | 2 / 0 | 0 / 0 / 0 | Schokopudding, Vanillepudding, ... per flavour |
| 1731 Katzenfutter nass | wet cat food: Felix (So gut wie es aussieht, Doppelt lecker, Portionsbeutel), Gourmet Gold Feine Pastete / Perle, Whiskas | 13 / 0 | 0 / 0 / 0 | per type (Pastete, Häppchen in Soße / Gelee, Perle, Portionsbeutel); per brand is not a variety |
| 1736 Instantnudeln | instant noodles: Nissin Soba cup / bag, Nongshim noodle soup, Maggi 5 Minuten Terrine, Samyang Buldak spicy ramen, Taste of Asia Ramen / Udon | 9 / 0 | 0 / 0 / 0 | Instant-Soba, Instant-Ramen (mild), Instant-Ramen scharf (Buldak), Instant-Udon, Nudelsuppe, Terrine (Fertiggericht) |
| 1743 Biermix | 'Biermix' = Schöfferhofer Weizen Mix Grapefruit and other flavoured beer mixes | 1 / 0 | 0 / 0 / 0 | per flavour: Biermix Grapefruit, Biermix Zitrone, ... |
| 1744 Biermix alkoholfrei | 'Biermix alkoholfrei' = Paulaner Weißbier-Zitrone 0,0, Schöfferhofer Weizen Mix alkoholfrei | 2 / 0 | 0 / 0 / 0 | per flavour; also decide whether Paulaner Weißbier-Zitrone belongs to 1742 Radler alkoholfrei (it is wheat beer with lemonade, which Paulaner sells as a Radler) |
| 1760 Wasser mit Geschmack | 'Wasser mit Geschmack' = Rheinfels Mineralwasser (plain water!), Hella near water, Volvic Tee / Touch / Vitamin, Hella Erfrischungsgetränk | 7 / 0 | 0 / 0 / 0 | Rheinfels Mineralwasser belongs to the water items (1705 / 1758 / 1759); the flavoured waters per flavour or per type (Tee, Touch, Vitamin) |
| 1771 Cider | 'Cider' = Somersby (apple) and Blackberry Cider (receipt rule) | 1 / 0 | 0 / 0 / 1 | Apfelcider and Brombeercider |
| 1772 Weinbrand / Brandy | 'Weinbrand / Brandy' = Asbach, Chantré, Veterano | 5 / 0 | 0 / 0 / 0 | Weinbrand (Asbach, Chantré) and Brandy (Veterano) as two items |
| 1773 Anisspirituose | 'Anisspirituose' = Pernod, Küstennebel | 2 / 0 | 0 / 0 / 0 | Pastis (Pernod), Anisschnaps (Küstennebel); Ouzo if it appears (already on the batch-2 list) |
| 1787 Tassimo T-Discs | Tassimo T-Discs: Jacobs Latte Macchiato Classico, Morning Café, generic 'kaffeekapseln' (also the Tassimo machine key) | 6 / 0 | 0 / 0 / 0 | per variety (Latte Macchiato, Café Crema, Espresso, ...); 'Tassimo Happy Kapselmaschine' is a machine, not a T-Disc |
| 1793 Tafelschokolade Vollmilch | 'Tafelschokolade Vollmilch' = Milka Großtafel, MMMAX, Sweet Winter (seasonal flavour), Toblerone, plain 'Milka' | 6 / 0 | 0 / 0 / 0 | Alpenmilch (plain Vollmilch), Haselnuss, Oreo, Sweet Winter, ... per flavour; Toblerone is its own item (honey-almond nougat) |
| 1797 Gewürzgurken (Glas) | 'Gewürzgurken (Glas)' = All Seasons Gurken, Trader Joe's Sandwich Gurken | 2 / 0 | 0 / 0 / 0 | Sandwich-Gurken (sliced) as their own item; Gewürzgurken stays whole pickles (Cornichons 916 and Mixed Pickles 918 already exist) |
| 1799 Eisriegel | 'Eisriegel' = Mars ice cream, Snickers ice cream | 2 / 0 | 0 / 0 / 0 | Mars-Eisriegel, Snickers-Eisriegel |
| 1680 Schokoriegel | 'Schokoriegel' = Duplo, Mars, Twix (and the store product 'Kinder Bueno' is linked here although the override says 1727) | 3 / 0 | 2 / 0 / 0 | Duplo, Mars, Twix, Snickers, ... each its own item; fix the Kinder Bueno link |
| 1690 Schokolinsen | 'Schokolinsen' = Maltesers (malt balls, not lentils) | 1 / 0 | 1 / 0 / 0 | Maltesers (Schokokugeln mit Malz) as their own item; Schokolinsen stays M&M-type |
| 1679 Senf | 'Senf' (spices & seasonings, Glas) is also the target of the store key 'mittelscharfer senf', while recipe overrides map 'mittelscharfer senf' to 937 senf mittelscharf | 1 / 0 | 1 / 0 / 0 | decide the standard mustard: 937 (condiments & sauces, g) or 1679; batch 2b maps plain 'Senf' to 1679 as instructed and adds the mustard varieties as new items (1834 Chipotle, 1835 Kräuter, 1836 Bier) |
| 1788 Butter-Mischstreichfett | 'Butter-Mischstreichfett' = Kerrygold Extra, Arla Kärgården (3 keys), Meggle Streichzart | 6 / 0 | 0 / 0 / 0 | check which of these are butter-oil blends and which are plain butter (374 butter); per product type |
| 1724 Spülmittel | 'Spülmittel' = Fairy (concentrate), Fit, Pril | 6 / 0 | 0 / 0 / 0 | concentrate vs standard; scent only if you want it tracked |
| 1810 Duschgel | 'Duschgel' = Nivea Pflegedusche, Palmolive, Dresdner Essenz, Fa | 4 / 0 | 0 / 0 / 0 | scent variants only if you want them tracked (low value) |

## 2. Receipt lines matched by rule or override (live: 185 of 509 lines have a match source)

Only the lines where a variety was collapsed are listed; the other rule / override lines (Butter, Gurke, Spaghetti, Tomatenmark, ...) are the plain product and need no split. Query: `purchase_receipt_line` joined to `inventory_item`, `purchase_receipt_line_match_source` is `rule` or `override`.

| receipt line | matched item | live rows | proposed |
|---|---|---|---|
| Blackberry Cider | 1771 Cider | 1 line | Brombeercider (own item); Somersby = Apfelcider |
| PAPRIKA LYONER | 305 lyoner | 1 line | Paprika-Lyoner (own item; plain Lyoner stays 305) |
| YUCATANHONIG | 1070 honig | 1 line | Honig mit Sortenangabe: own item (like 1833 Bio-Sommerblütenhonig) or keep as plain Honig |
| LH GOUDA JUNG | 399 gouda | 5 lines | junger Gouda = 400 junger gouda (exists); 399 is plain gouda |
| EDELSALAMI | 302 salami | 1 line | Edelsalami vs Salami (see 302 below) |
| Bagels Mehrkorn | 587 bagel vollkorn | 5 lines | Mehrkorn-Bagel is not 587 bagel vollkorn: own item (584 bagel natur, 585 sesam, 586 mohn, 588 everything exist) |
| Landliebe Konfit | 1689 Konfitüre | 2 lines | fruit not on the receipt: stays 1689 or review |
| Corny Müsliriege | 1477 müsliriegel | 3 lines | flavour not on the receipt (Müsliriegel flavours: Schoko, Erdbeer, Banane ...) |
| Apfel rot / APFEL | 143 apfel | 3 lines | colour / variety apples (Rot, Gala, Braeburn ...) |
| Paprika rot / rot kg / PAPRIKA | 54 paprika | 6 lines | Paprika rot vs gelb vs grün (they are different vegetables on the shelf) |
| Bio Bananen / Bananen | 199 banane | 4 lines | organic vs conventional: is 'Bio' a variety under the rule? (Nate decides; brand-like attribute) |
| Bio Karotten / Karotten 2kg | 1686 Karotten | 2 lines | same question for 'Bio'; the 2 kg is only a pack size |
| Naturgut Brühe | 783 gemüsebrühe pulver | 3 lines | an unspecified 'Brühe' is now 1851 Brühe (possible Rind / Hähnchen); the override sends it to 783 gemüsebrühe pulver |
| Coca Cola / Coca Cola 2L | 1698 Cola | 4 lines | Cola classic is right; Pepsi and the store keys also map here (brand only) |
| Kartoffeln 2,5kg / 4kg | 31 kartoffel | 3 lines | pack sizes only: no split |

The other 324 receipt lines have no match source (not matched at all). Counts above are lines, not money.


## 3. Store products already linked to ONE item although they differ (live `store_product.store_product_inventory_item_id`)

375 store products are linked to an item. These are the items that carry several differing products (each product's own store offers follow it). Real variants are listed in 3a; products that are simply the **wrong** item are in 3b.

### 3a. Real variants under one item

| item | products linked now | live rows (products / offers) | proposed |
|---|---|---|---|
| 569 brötchen | 20 products: Aufback-, Bäcker-, Bio-Land-, Dinkel-Steinofen-, Finnkorn-Toast-, Fitness-, Französische Schoko-, Hamburger-, Kaiser-, Kürbiskern-Kartoffel-, Meisterbäcker-, Milch-, Pizza-, Protein-, Rosen-, Walnuss-, Weizen-/Baguette-, Weizen-, Weltmeister-Chia-Brötchen | 20 / 0 | own items per type; several already exist: 570 vollkorn, 571 dinkel, 572 laugen, 573 sesam, 574 mohn, 575 rosinen, 576 mehrkorn, 577 kaiser, 578 käse, 579 zwiebel, 582 burger, 583 hot dog (Hamburger-Brötchen → 582, Kaiserbrötchen → 577, Dinkel-Steinofen → 571, Finnkorn / Chia / Kürbiskern → 576 mehrkorn?, Schokobrötchen → 592-style sweet item) |
| 302 salami | 10 products: Edelsalami, Genießer-Salami, Herzhafte Edelsalami, Salami, Die Ofenfrische / Baguettes / Piccolinis / Steinofen-Pizza Salami, Vegane Salami | 10 / 0 | Edelsalami vs Salami; the pizza / baguette 'Salami' lines are pizzas (1677) and baguettes (561), not salami; Vegane Salami belongs to plant-based |
| 356 vollmilch | 7 products: ESL Milch 3.5 %, Frische / Haltbare (Bio-)Vollmilch, Nussbeisser Edel-Vollmilch, Premium-Schokolade Edel-Vollmilch | 7 / 0 | fresh vs long-life (haltbar) milk and organic: decide; the two chocolates are 3b |
| 561 baguette | 7 products: Baguettes, Bauernbaguette, Belegtes Baguette, Französisches Steinofen-Baguette, Gewürzbutter-/Kräuterbutter-Baguette, Zwiebelbaguette | 7 / 0 | Baguette (plain), Kräuterbutter-Baguette, Zwiebelbaguette, belegtes Baguette (a ready meal) |
| 610 waffel | 7 products: Feine Butterwaffeln, Frischei-Waffeln, Mini-Waffeln, Sandwich-Waffeln, Schoko-Waffeln, Waffeln | 7 / 0 | Butterwaffeln, Schokowaffeln, Mini-Waffeln as separate items |
| 374 butter | 6 products: Bio-Butter, Butter, Deutsche Markenbutter, Irische Butter, Kerrygold extra, Kräuter-/Knoblauch-Butter | 6 / 0 | Kräuter-/Knoblauch-Butter = 1789 Kräuterbutter (exists); Kerrygold extra = 1788; Irische Butter (salted?) and Bio-Butter: decide |
| 270 rinderhackfleisch | 6 products: Fettreduziertes Rinderhackfleisch, Hackfleisch gemischt (!), Rinderhackfleisch, XXL | 6 / 0 | fettreduziert as its own item; 'Hackfleisch gemischt' belongs to 271 |
| 306 fleischwurst | 4 products: Geflügel-Fleischwurst, Premium Fleischwurst, Schinken-Fleischwurst | 4 / 0 | Geflügel-Fleischwurst = 1820 (exists); Schinken-Fleischwurst own item |
| 638 haferflocken | 3 products: Blütenzarte, Kernige, Zarte Bio-Haferflocken | 3 / 0 | zarte vs kernige Haferflocken |
| 948 mayonnaise | 3 products: Delikatess-Mayonnaise (2 spellings), Salat-Mayonnaise | 3 / 0 | Delikatess- vs Salatmayonnaise |
| 31 kartoffel | 3 products: Kartoffeln vfk, Speisekartoffeln, Marzipan-Kartoffeln (3b) | 3 / 1 | vorwiegend festkochend vs mehligkochend vs festkochend (the recipes already use 31 for all three) |
| 40 zwiebel | 4 products: Zwiebeln, Bio Zwiebeln rot NL, Gemüsezwiebeln, Röstzwiebeln (3b) | 4 / 1 | rote Zwiebel = 41 (exists); Gemüsezwiebel own item |
| 381 frischkäse | 4 products: Frischkäse (3 spellings), Körniger Frischkäse | 4 / 0 | körniger Frischkäse is cottage cheese: own item |
| 369 crème fraîche | 2 products: Crème fraîche, Crème fraîche leicht | 2 / 0 | leicht (light) as its own item |
| 202 kiwi | 3 products: Kiwi grün, Kiwis grün, SunGold Kiwi | 3 / 0 | SunGold (gelbe Kiwi) as its own item |
| 484 fischstäbchen | 3 products: Fischstäbchen, Backfischstäbchen, Backfisch-Stäbchen / Fischstäbchen | 3 / 0 | Backfischstäbchen (breaded fillet) vs Fischstäbchen: decide |
| 493 räucherlachs | 3 products: Bio-, Norwegischer, Skandinavischer Räucherlachs | 3 / 0 | origin / organic only: probably one item |
| 442 forellenfilet | 2 products: Forellenfilets, Lachsforellenfilet | 2 / 0 | Lachsforelle vs Forelle: own item |
| 590 croissant | 2 products: Croissants, Nuss-Nougat-Croissant | 2 / 0 | Nuss-Nougat-Croissant (own item; 592 schokocroissant, 593 mandelcroissant exist) |
| 50 tomate | 2 products: Bio-Tomaten, Bio-Gehackte Tomaten | 2 / 0 | gehackte Tomaten are a canned product (823 tomaten in stücken dose exists) |
| 92 spinat | 2 products: Rahm-Spinat, Rahmspinat | 2 / 0 | Rahmspinat is a frozen / ready product, not 92 spinat |
| 181 orange | 2 products: Mandarin-Orangen, Orangen XXL | 2 / 0 | Mandarinen = 183 (exists) |
| 1055 essig | 2 products: Essig, Wein-Branntwein-Essig | 2 / 0 | Weinessig / Branntweinessig: decide (Weißweinessig 1050 exists) |
| 386 naturjoghurt | 2 products: Joghurt 3.5 % 4er, Fruchtzwerge | 2 / 0 | Fruchtzwerge are a fruit quark: own item (the store key maps it to 386) |

### 3b. Linked to the wrong item (not a variant question; listed because the same pass found them)

| store product | linked to | should probably be |
|---|---|---|
| Marzipan-Kartoffeln | 31 kartoffel | 1813 Marzipankartoffeln |
| Röstzwiebeln | 40 zwiebel | a dried / fried onion product (no item yet) |
| Instant-Krümeltee Zitrone, Waldfrucht, Pfirsich | 148 pfirsich | a tea (no item; not a peach) |
| Trockenpflaumen | 151 pflaume | dried plums (no item; not 151 pflaume) |
| Fisherman’s Friend Cherry | 154 kirsche | a lozenge (not 154 kirsche) |
| Fisherman’s Friend Mint | 138 minze | a lozenge (not 138 minze) |
| Premium-Stieleis Mandel | 221 mandel | an ice lolly (1726 Stieleis) |
| Nussbeisser Edel-Vollmilch, Premium-Schokolade Edel-Vollmilch | 356 vollmilch | chocolates (1793 Tafelschokolade Vollmilch) |
| Chips Gesalzen, Chips Sour Cream & Onion | 368 saure sahne | chips (1331 kartoffelchips gesalzen; 1334 kartoffelchips sauerrahm zwiebel) |
| Pizzateig mit Tomatensauce | 959 tomatensauce | a pizza dough (1622 frischer Pizzateig) |
| Hackfleisch gemischt | 270 rinderhackfleisch | 271 gemischtes hackfleisch |
| Kinder Bueno | 1680 Schokoriegel | 1727 Waffelschnitte (the override already says so) |
| Fruchtzwerge | 386 naturjoghurt | own item (see above) |

## 4. Catalogue items whose own name combines varieties

| item | combines | proposed |
|---|---|---|
| 1696 Helles / Lager | Helles and Lager | two items (see section 1) |
| 1737 Landbier / Kellerbier | Landbier and Kellerbier | two items |
| 1714 Kräuterlikör / Bitter | Kräuterlikör and Bitter (and Amaro, Fernet by the keys) | see section 1 |
| 1772 Weinbrand / Brandy | Weinbrand and Brandy | two items |
| 1806 Fleckenentferner flüssig / Gel | liquid and gel | two items, or one if the form does not matter for shopping |
| 1691 Cola-Mix (Spezi) | Spezi only | fine |
| 1716 Mixgetränk (alkoholisch), 1743 Biermix, 1744 Biermix alkoholfrei, 1760 Wasser mit Geschmack, 1745 Bier mit Geschmack, 1706 Smoothie, 1704 Eistee, 1762 Saftshot, 1764 Proteindrink | one item for every flavour | per flavour (needs the flavour per product) |
| 1735 Hundesnacks, 1733 Katzensnacks, 1731 / 1734 nass | one item for every sort | per sort |
| 1796 Schokoladenstücke (Beutel) | baking chocolate pieces and Ritter Sport Knusperstücke | decide |

Items 1-1675 are the original seed (generic category items with no flavour split: `milch`, `joghurt` ...); they were checked only where live rows or override keys point at them.

## 5. Decisions for Nate

1. For each row of sections 1 and 3a: split yes / no, and which new items. Nothing changes until then.
2. ~~The standard mustard~~ **Decided by Nate (2026-10-22): 937 senf mittelscharf.** Plain "Senf" in recipes and the store key "mittelscharfer senf" both map to 937 now; 1679 "Senf" (spices, Glas) is a probable duplicate — decide whether to merge or keep it.
3. Is **"Bio"** a variety (Bio-Bananen, Bio-Karotten, Bio-Butter, Bio-Vollmilch ...)? Batch 2b makes Bio-Sommerblütenhonig its own item because it is a honey variety, not because of the "Bio".
4. **Paulaner Weißbier-Zitrone 0,0** (the product behind "Paulaner 2in1 alkoholfreies Bier"): 1744 Biermix alkoholfrei (what the rematch uses) or 1742 Radler alkoholfrei.
5. **1893 "SalaRico"** carries a brand / trade name, against the item rule. A web search (2026-10-22) found no product of that name; check the recipes that use it (5 lines) and give the item its generic lettuce name.
6. **Brühe**: an unspecified "Naturgut Brühe" on a receipt now fits 1851 Brühe better than 783 gemüsebrühe pulver.
7. The wrong links in 3b: relink or unlink (a separate guarded fix; needs a snapshot first).

How to apply an accepted row (not done here): add the new items in a numbered migration (pattern 0015), map the store / receipt keys in the override files, and re-run the matching with a dry run first; any change to live rows goes through a snapshot (CLAUDE.md rule 3).

