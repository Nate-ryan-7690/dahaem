-- Catalogue batch 2b: new items for the recipe lines that still had none after rematch pass 2 (MariaDB). Nate's
-- decisions of 2026-10-22, under the tightened item rule (CLAUDE.md "Codebase conventions", docs/DATABASE.md):
-- if it is its own product on the store shelf, it is its own inventory_item -- varieties and flavours included; only
-- the brand is abstracted away. Ids 1833-1899 (67 new items); the highest id before this was 1832 (migration 0014).
--
-- NEW items, category / subcategory / unit of the closest sibling in the live catalogue, animals in
-- inventory_item_animal (CERTAIN where the name says it, POSSIBLE where the product usually contains meat but the name
-- does not say which). Items without an animal row are meat- and fish-free as far as the product pages say.
--
-- Varieties and products Nate named:
--   1833 Bio-Sommerblütenhonig                      baking supplies / sugars & sweeteners / g  [-]
--   1834 Chipotle-Senf                              condiments & sauces / mustard & mayonnaise / g  [-]
--   1835 Kräutersenf                                condiments & sauces / mustard & mayonnaise / g  [-]
--   1836 Biersenf                                   condiments & sauces / mustard & mayonnaise / g  [-]
--   1837 Kirschkonfitüre                            baking supplies / spreads / g  [-]
--   1838 Pastrami mit Pfefferrand                   meat / wurst & aufschnitt / g  [Rind CERTAIN]
--   1839 Bunte Karotten                             vegetable / - / g  [-]
--   1840 gekochte Eier                              dairy / - / Stück  [-]
--   1841 Knoblauch-Ingwer-Paste                     condiments & sauces / - / g  [-]
--   1842 Knoblauch-Zwiebel-Paste                    condiments & sauces / - / g  [-]
--   1843 BBQ-Soße Honig-Senf                        condiments & sauces / marinades & bbq sauces / g  [-]
--   1844 Ziegenfrischkäse-Crumble mit Honig         dairy / cheese / g  [-]
--   1845 grüner Pfeffer in Lake                     canned & jarred goods / jarred vegetables & pickles / g  [-]
--   1846 heller Balsamico                           oils & vinegars / - / ml  [-]
--   1847 Ravioli mit Nduja                          pasta / filled pasta / g  [Schwein CERTAIN]
--   1848 Schoko-Ostereier                           sweets & snacks / chocolate / g  [-]
--   1849 Karotten-Kokos-Suppe                       chilled / - / ml  [-]
--   1850 Karotten-Buns                              bakery / - / g  [-]
--   1851 Brühe                                      dry goods / dried soup & stock / g  [Rind POSSIBLE, Hähnchen POSSIBLE]
--
-- Every no-item name with >= 5 lines in rematch_20261015-072912_noitem.tsv (names under 5 lines stay unmatched, backlog):
--   1852 Süße Soße Asiatische Art                   condiments & sauces / asian sauces / g  [-]
--   1853 Spekulatius-Kekse                          sweets & snacks / biscuits / g  [-]
--   1854 Mini-Spekulatius                           sweets & snacks / biscuits / g  [-]
--   1855 Gemüsemix Lauch, Champignons, Karotte      vegetable / - / g  [-]
--   1856 Bruschetta-Dip                             snacks / dips & spreads / g  [-]
--   1857 Champignons und Lauch, geschnitten         vegetable / - / g  [-]
--   1858 Heumilchkäse mit Kräuterrinde              dairy / cheese / g  [-]
--   1859 Burger-Sauce                               condiments & sauces / other condiments / g  [-]
--   1860 Veganes Western-Dressing                   condiments & sauces / dressings / g  [-]
--   1861 Asian Stir Fry Sauce                       condiments & sauces / asian sauces / g  [-]
--   1862 Polpa Chili                                canned & jarred goods / canned vegetables / g  [-]
--   1863 Himbeer-Balsamico-Dressing                 condiments & sauces / dressings / g  [-]
--   1864 Kaminkäse                                  dairy / cheese / g  [-]
--   1865 Frucht-Balsamico-Dressing                  condiments & sauces / dressings / g  [-]
--   1866 Kräutermix für Grüne Soße                  culinary herbs / - / g  [-]
--   1867 gelbe Bete                                 vegetable / - / g  [-]
--   1868 Cheesecake Rote Früchte                    bread & bakery / cakes / g  [-]
--   1869 Pikantes Zitrus-Dressing                   condiments & sauces / dressings / g  [-]
--   1870 Porridge exotische Früchte                 grains & rice / grains & oats / g  [-]
--   1871 Mini-Pancakes                              bread & bakery / pastry & sweet bakery / g  [-]
--   1872 Mediterraner Gemüsemix                     vegetable / - / g  [-]
--   1873 Sonntagsknödel                             pantry / - / g  [-]
--   1874 Antipasti-Trio                             canned & jarred goods / jarred vegetables & pickles / g  [-]
--   1875 Kebab-Paste                                condiments & sauces / - / g  [-]
--   1876 Müslikugeln-Backmischung                   baking supplies / baking mixes / g  [-]
--   1877 Chocolate Chip Cookies                     sweets & snacks / biscuits / g  [-]
--   1878 Mürbteig-Backmischung                      baking supplies / baking mixes / g  [-]
--   1879 Torchietti                                 pasta / dried pasta / g  [-]
--   1880 Grillbrot-Backmischung                     baking supplies / baking mixes / g  [-]
--   1881 Rahmjoghurt                                dairy / yogurt / g  [-]
--   1882 Frühstücksherzen                           bread & bakery / rolls & buns / g  [-]
--   1883 Frühstücksherzen Mehrkorn                  bread & bakery / rolls & buns / g  [-]
--   1884 Lemon Cheesecake                           bread & bakery / cakes / g  [-]
--   1885 Schokoladiger Lava Cake                    bread & bakery / pastry & sweet bakery / g  [-]
--   1886 Nuss-Frucht-Mix                            snacks / nuts & trail mix / g  [-]
--   1887 Gefüllte Weinblätter                       canned & jarred goods / jarred vegetables & pickles / g  [-]
--   1888 dunkle Weintrauben                         fruit / - / g  [-]
--   1889 Crunchy Mix Smokey BBQ                     snacks / popcorn & puffed snacks / g  [-]
--   1890 Schokolade 70 %                            baking supplies / chocolate & cocoa / g  [-]
--   1891 Nuss-Frucht-Mix Schokolade                 snacks / nuts & trail mix / g  [-]
--   1892 vegane Kräutercrème                        snacks / dips & spreads / g  [-]
--   1893 SalaRico                                   vegetable / - / Stück  [-]
--   1894 bunte Gemüsewürfel                         vegetable / - / g  [-]
--   1895 Braune Soße                                condiments & sauces / other condiments / g  [Rind POSSIBLE]
--   1896 Olivenaufstrich                            snacks / dips & spreads / g  [-]
--   1897 Weißer Reis, vorgekocht                    grains & rice / rice / g  [-]
--   1898 Boemboe Rendang                            condiments & sauces / - / g  [-]
--   1899 Rindfleisch                                meat / fresh meat / g  [Rind CERTAIN]
--
-- NOT new (the catalogue already has the product; mapped in scripts/importer/overrides.json instead):
--   "Honig-Senf-Dressing"            -> 992 honigsenf dressing      (an item of the same product exists)
--   "Paulaner 2in1 alkoholfreies Bier" -> 1744 Biermix alkoholfrei   (alcohol-free wheat beer + lemonade; 1742 Radler alkoholfrei is the near alternative)
--   the review-TSV mappings of Nate (Joghurt 386, Tahini-Paste 913, Kapern 929, Piment 1190, Senf 1679, Gemüsebrühe 780,
--   vegane Knusperstäbchen 1826, afri cola 1698, dl Olivenöl 1022, mg Balsamicoessig 1053, gemischtes Hackfleisch mit Salz
--   und Pfeffer 271, Skipjack Thunfisch in Sonnenblumenöl 849 thunfisch in öl dose). "Pastrami mit Pfefferrand" no longer
--   maps to 1829 Pastrami: it is its own item now.
--
-- What it does: it ONLY ADDS (INSERT IGNORE: {len(items)} items, {len(ani_sql.splitlines())} animal rows). Nothing is changed or deleted. Re-runnable.
-- The same rows are in seed/inventory_catalog.sql. Needs migration 0012 (inventory_item_animal_certainty).
-- The last statement checks everything: every line must say OK. "CHECK" means an id is used by another item on this
-- database or an animal row is missing -- then stop and ask before running the rematch.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3, into ~/dahaem-frontend-live/sql/snapshots/, keep at most 3), then apply from the
-- frontend project folder (the one with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0015_catalog_batch_2b.sql
-- Not applied to the live database by the PR that adds it.

SET NAMES utf8mb4;

INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1833, 'organic summer blossom honey', 'Bio-Sommerblütenhonig', 0, 'g', 'baking supplies', 'sugars & sweeteners', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1834, 'chipotle mustard', 'Chipotle-Senf', 0, 'g', 'condiments & sauces', 'mustard & mayonnaise', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1835, 'herb mustard', 'Kräutersenf', 0, 'g', 'condiments & sauces', 'mustard & mayonnaise', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1836, 'beer mustard', 'Biersenf', 0, 'g', 'condiments & sauces', 'mustard & mayonnaise', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1837, 'cherry jam', 'Kirschkonfitüre', 0, 'g', 'baking supplies', 'spreads', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1838, 'pepper-crusted pastrami', 'Pastrami mit Pfefferrand', 0, 'g', 'meat', 'wurst & aufschnitt', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1839, 'rainbow carrots', 'Bunte Karotten', 0, 'g', 'vegetable', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1840, 'cooked eggs', 'gekochte Eier', 0, 'Stück', 'dairy', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1841, 'garlic and ginger paste', 'Knoblauch-Ingwer-Paste', 0, 'g', 'condiments & sauces', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1842, 'garlic and onion paste', 'Knoblauch-Zwiebel-Paste', 0, 'g', 'condiments & sauces', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1843, 'honey mustard BBQ sauce', 'BBQ-Soße Honig-Senf', 0, 'g', 'condiments & sauces', 'marinades & bbq sauces', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1844, 'goat''s cream cheese crumble with honey', 'Ziegenfrischkäse-Crumble mit Honig', 0, 'g', 'dairy', 'cheese', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1845, 'green peppercorns in brine', 'grüner Pfeffer in Lake', 0, 'g', 'canned & jarred goods', 'jarred vegetables & pickles', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1846, 'white balsamic vinegar', 'heller Balsamico', 0, 'ml', 'oils & vinegars', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1847, 'nduja ravioli', 'Ravioli mit Nduja', 0, 'g', 'pasta', 'filled pasta', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1848, 'chocolate easter eggs', 'Schoko-Ostereier', 0, 'g', 'sweets & snacks', 'chocolate', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1849, 'carrot and coconut soup', 'Karotten-Kokos-Suppe', 0, 'ml', 'chilled', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1850, 'carrot buns', 'Karotten-Buns', 0, 'g', 'bakery', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1851, 'stock (unspecified)', 'Brühe', 0, 'g', 'dry goods', 'dried soup & stock', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1852, 'sweet Asian-style sauce', 'Süße Soße Asiatische Art', 0, 'g', 'condiments & sauces', 'asian sauces', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1853, 'speculoos biscuits', 'Spekulatius-Kekse', 0, 'g', 'sweets & snacks', 'biscuits', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1854, 'mini speculoos biscuits', 'Mini-Spekulatius', 0, 'g', 'sweets & snacks', 'biscuits', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1855, 'leek, mushroom and carrot vegetable mix', 'Gemüsemix Lauch, Champignons, Karotte', 0, 'g', 'vegetable', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1856, 'bruschetta dip', 'Bruschetta-Dip', 0, 'g', 'snacks', 'dips & spreads', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1857, 'sliced mushrooms and leek', 'Champignons und Lauch, geschnitten', 0, 'g', 'vegetable', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1858, 'hay milk cheese with herb rind', 'Heumilchkäse mit Kräuterrinde', 0, 'g', 'dairy', 'cheese', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1859, 'burger sauce', 'Burger-Sauce', 0, 'g', 'condiments & sauces', 'other condiments', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1860, 'vegan western dressing', 'Veganes Western-Dressing', 0, 'g', 'condiments & sauces', 'dressings', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1861, 'Asian stir fry sauce', 'Asian Stir Fry Sauce', 0, 'g', 'condiments & sauces', 'asian sauces', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1862, 'chili tomato polpa', 'Polpa Chili', 0, 'g', 'canned & jarred goods', 'canned vegetables', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1863, 'raspberry balsamic dressing', 'Himbeer-Balsamico-Dressing', 0, 'g', 'condiments & sauces', 'dressings', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1864, 'oven-baked camembert-style cheese', 'Kaminkäse', 0, 'g', 'dairy', 'cheese', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1865, 'fruit balsamic dressing', 'Frucht-Balsamico-Dressing', 0, 'g', 'condiments & sauces', 'dressings', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1866, 'herb mix for green sauce', 'Kräutermix für Grüne Soße', 0, 'g', 'culinary herbs', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1867, 'yellow beetroot', 'gelbe Bete', 0, 'g', 'vegetable', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1868, 'red fruit cheesecake', 'Cheesecake Rote Früchte', 0, 'g', 'bread & bakery', 'cakes', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1869, 'spicy citrus dressing', 'Pikantes Zitrus-Dressing', 0, 'g', 'condiments & sauces', 'dressings', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1870, 'exotic fruit porridge', 'Porridge exotische Früchte', 0, 'g', 'grains & rice', 'grains & oats', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1871, 'mini pancakes', 'Mini-Pancakes', 0, 'g', 'bread & bakery', 'pastry & sweet bakery', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1872, 'Mediterranean vegetable mix', 'Mediterraner Gemüsemix', 0, 'g', 'vegetable', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1873, 'Sunday dumplings', 'Sonntagsknödel', 0, 'g', 'pantry', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1874, 'antipasti trio', 'Antipasti-Trio', 0, 'g', 'canned & jarred goods', 'jarred vegetables & pickles', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1875, 'kebab paste', 'Kebab-Paste', 0, 'g', 'condiments & sauces', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1876, 'muesli ball mix (DIY)', 'Müslikugeln-Backmischung', 0, 'g', 'baking supplies', 'baking mixes', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1877, 'chocolate chip cookies', 'Chocolate Chip Cookies', 0, 'g', 'sweets & snacks', 'biscuits', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1878, 'shortcrust pastry mix', 'Mürbteig-Backmischung', 0, 'g', 'baking supplies', 'baking mixes', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1879, 'torchietti pasta', 'Torchietti', 0, 'g', 'pasta', 'dried pasta', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1880, 'grill bread mix', 'Grillbrot-Backmischung', 0, 'g', 'baking supplies', 'baking mixes', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1881, 'cream yogurt', 'Rahmjoghurt', 0, 'g', 'dairy', 'yogurt', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1882, 'breakfast hearts (bake-off rolls)', 'Frühstücksherzen', 0, 'g', 'bread & bakery', 'rolls & buns', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1883, 'multigrain breakfast hearts (bake-off rolls)', 'Frühstücksherzen Mehrkorn', 0, 'g', 'bread & bakery', 'rolls & buns', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1884, 'lemon cheesecake', 'Lemon Cheesecake', 0, 'g', 'bread & bakery', 'cakes', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1885, 'chocolate lava cake', 'Schokoladiger Lava Cake', 0, 'g', 'bread & bakery', 'pastry & sweet bakery', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1886, 'nut and fruit mix', 'Nuss-Frucht-Mix', 0, 'g', 'snacks', 'nuts & trail mix', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1887, 'stuffed vine leaves with rice', 'Gefüllte Weinblätter', 0, 'g', 'canned & jarred goods', 'jarred vegetables & pickles', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1888, 'dark grapes', 'dunkle Weintrauben', 0, 'g', 'fruit', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1889, 'crunchy mix smokey BBQ', 'Crunchy Mix Smokey BBQ', 0, 'g', 'snacks', 'popcorn & puffed snacks', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1890, 'chocolate 70%', 'Schokolade 70 %', 0, 'g', 'baking supplies', 'chocolate & cocoa', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1891, 'chocolate-coated nut and fruit mix', 'Nuss-Frucht-Mix Schokolade', 0, 'g', 'snacks', 'nuts & trail mix', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1892, 'vegan herb cream spread', 'vegane Kräutercrème', 0, 'g', 'snacks', 'dips & spreads', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1893, 'SalaRico lettuce', 'SalaRico', 0, 'Stück', 'vegetable', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1894, 'mixed vegetable cubes', 'bunte Gemüsewürfel', 0, 'g', 'vegetable', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1895, 'brown gravy', 'Braune Soße', 0, 'g', 'condiments & sauces', 'other condiments', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1896, 'olive spread', 'Olivenaufstrich', 0, 'g', 'snacks', 'dips & spreads', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1897, 'pre-cooked white rice', 'Weißer Reis, vorgekocht', 0, 'g', 'grains & rice', 'rice', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1898, 'boemboe rendang spice paste', 'Boemboe Rendang', 0, 'g', 'condiments & sauces', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1899, 'beef', 'Rindfleisch', 0, 'g', 'meat', 'fresh meat', NULL);

INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1838, 'Rind', 'certain');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1847, 'Schwein', 'certain');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1851, 'Rind', 'possible');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1851, 'Hähnchen', 'possible');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1895, 'Rind', 'possible');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1899, 'Rind', 'certain');

SELECT e.what, CASE WHEN i.inventory_item_id IS NOT NULL
                         AND (e.name IS NULL OR i.inventory_item_name_deutsch = e.name)
                         AND (e.category IS NULL OR i.inventory_item_category = e.category)
                         AND (SELECT COUNT(*) FROM inventory_item_animal a
                              WHERE a.inventory_item_animal_item_id = e.id
                                AND a.inventory_item_animal_certainty = 'certain') >= e.certain_rows
                         AND (SELECT COUNT(*) FROM inventory_item_animal a
                              WHERE a.inventory_item_animal_item_id = e.id
                                AND a.inventory_item_animal_certainty = 'possible') = e.possible_rows
                    THEN 'OK'
                    ELSE CONCAT('CHECK: id is ', COALESCE(i.inventory_item_name_deutsch, 'missing'), ' / ',
                                COALESCE(i.inventory_item_category, '-'))
               END AS result
FROM (SELECT 1833 AS id, 'new item' AS what, 'Bio-Sommerblütenhonig' AS name, 'baking supplies' AS category, 0 AS certain_rows, 0 AS possible_rows
      UNION ALL SELECT 1834, 'new item', 'Chipotle-Senf', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1835, 'new item', 'Kräutersenf', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1836, 'new item', 'Biersenf', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1837, 'new item', 'Kirschkonfitüre', 'baking supplies', 0, 0
      UNION ALL SELECT 1838, 'new item + animal', 'Pastrami mit Pfefferrand', 'meat', 1, 0
      UNION ALL SELECT 1839, 'new item', 'Bunte Karotten', 'vegetable', 0, 0
      UNION ALL SELECT 1840, 'new item', 'gekochte Eier', 'dairy', 0, 0
      UNION ALL SELECT 1841, 'new item', 'Knoblauch-Ingwer-Paste', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1842, 'new item', 'Knoblauch-Zwiebel-Paste', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1843, 'new item', 'BBQ-Soße Honig-Senf', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1844, 'new item', 'Ziegenfrischkäse-Crumble mit Honig', 'dairy', 0, 0
      UNION ALL SELECT 1845, 'new item', 'grüner Pfeffer in Lake', 'canned & jarred goods', 0, 0
      UNION ALL SELECT 1846, 'new item', 'heller Balsamico', 'oils & vinegars', 0, 0
      UNION ALL SELECT 1847, 'new item + animal', 'Ravioli mit Nduja', 'pasta', 1, 0
      UNION ALL SELECT 1848, 'new item', 'Schoko-Ostereier', 'sweets & snacks', 0, 0
      UNION ALL SELECT 1849, 'new item', 'Karotten-Kokos-Suppe', 'chilled', 0, 0
      UNION ALL SELECT 1850, 'new item', 'Karotten-Buns', 'bakery', 0, 0
      UNION ALL SELECT 1851, 'new item + possible animals', 'Brühe', 'dry goods', 0, 2
      UNION ALL SELECT 1852, 'new item', 'Süße Soße Asiatische Art', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1853, 'new item', 'Spekulatius-Kekse', 'sweets & snacks', 0, 0
      UNION ALL SELECT 1854, 'new item', 'Mini-Spekulatius', 'sweets & snacks', 0, 0
      UNION ALL SELECT 1855, 'new item', 'Gemüsemix Lauch, Champignons, Karotte', 'vegetable', 0, 0
      UNION ALL SELECT 1856, 'new item', 'Bruschetta-Dip', 'snacks', 0, 0
      UNION ALL SELECT 1857, 'new item', 'Champignons und Lauch, geschnitten', 'vegetable', 0, 0
      UNION ALL SELECT 1858, 'new item', 'Heumilchkäse mit Kräuterrinde', 'dairy', 0, 0
      UNION ALL SELECT 1859, 'new item', 'Burger-Sauce', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1860, 'new item', 'Veganes Western-Dressing', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1861, 'new item', 'Asian Stir Fry Sauce', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1862, 'new item', 'Polpa Chili', 'canned & jarred goods', 0, 0
      UNION ALL SELECT 1863, 'new item', 'Himbeer-Balsamico-Dressing', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1864, 'new item', 'Kaminkäse', 'dairy', 0, 0
      UNION ALL SELECT 1865, 'new item', 'Frucht-Balsamico-Dressing', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1866, 'new item', 'Kräutermix für Grüne Soße', 'culinary herbs', 0, 0
      UNION ALL SELECT 1867, 'new item', 'gelbe Bete', 'vegetable', 0, 0
      UNION ALL SELECT 1868, 'new item', 'Cheesecake Rote Früchte', 'bread & bakery', 0, 0
      UNION ALL SELECT 1869, 'new item', 'Pikantes Zitrus-Dressing', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1870, 'new item', 'Porridge exotische Früchte', 'grains & rice', 0, 0
      UNION ALL SELECT 1871, 'new item', 'Mini-Pancakes', 'bread & bakery', 0, 0
      UNION ALL SELECT 1872, 'new item', 'Mediterraner Gemüsemix', 'vegetable', 0, 0
      UNION ALL SELECT 1873, 'new item', 'Sonntagsknödel', 'pantry', 0, 0
      UNION ALL SELECT 1874, 'new item', 'Antipasti-Trio', 'canned & jarred goods', 0, 0
      UNION ALL SELECT 1875, 'new item', 'Kebab-Paste', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1876, 'new item', 'Müslikugeln-Backmischung', 'baking supplies', 0, 0
      UNION ALL SELECT 1877, 'new item', 'Chocolate Chip Cookies', 'sweets & snacks', 0, 0
      UNION ALL SELECT 1878, 'new item', 'Mürbteig-Backmischung', 'baking supplies', 0, 0
      UNION ALL SELECT 1879, 'new item', 'Torchietti', 'pasta', 0, 0
      UNION ALL SELECT 1880, 'new item', 'Grillbrot-Backmischung', 'baking supplies', 0, 0
      UNION ALL SELECT 1881, 'new item', 'Rahmjoghurt', 'dairy', 0, 0
      UNION ALL SELECT 1882, 'new item', 'Frühstücksherzen', 'bread & bakery', 0, 0
      UNION ALL SELECT 1883, 'new item', 'Frühstücksherzen Mehrkorn', 'bread & bakery', 0, 0
      UNION ALL SELECT 1884, 'new item', 'Lemon Cheesecake', 'bread & bakery', 0, 0
      UNION ALL SELECT 1885, 'new item', 'Schokoladiger Lava Cake', 'bread & bakery', 0, 0
      UNION ALL SELECT 1886, 'new item', 'Nuss-Frucht-Mix', 'snacks', 0, 0
      UNION ALL SELECT 1887, 'new item', 'Gefüllte Weinblätter', 'canned & jarred goods', 0, 0
      UNION ALL SELECT 1888, 'new item', 'dunkle Weintrauben', 'fruit', 0, 0
      UNION ALL SELECT 1889, 'new item', 'Crunchy Mix Smokey BBQ', 'snacks', 0, 0
      UNION ALL SELECT 1890, 'new item', 'Schokolade 70 %', 'baking supplies', 0, 0
      UNION ALL SELECT 1891, 'new item', 'Nuss-Frucht-Mix Schokolade', 'snacks', 0, 0
      UNION ALL SELECT 1892, 'new item', 'vegane Kräutercrème', 'snacks', 0, 0
      UNION ALL SELECT 1893, 'new item', 'SalaRico', 'vegetable', 0, 0
      UNION ALL SELECT 1894, 'new item', 'bunte Gemüsewürfel', 'vegetable', 0, 0
      UNION ALL SELECT 1895, 'new item + possible animals', 'Braune Soße', 'condiments & sauces', 0, 1
      UNION ALL SELECT 1896, 'new item', 'Olivenaufstrich', 'snacks', 0, 0
      UNION ALL SELECT 1897, 'new item', 'Weißer Reis, vorgekocht', 'grains & rice', 0, 0
      UNION ALL SELECT 1898, 'new item', 'Boemboe Rendang', 'condiments & sauces', 0, 0
      UNION ALL SELECT 1899, 'new item + animal', 'Rindfleisch', 'meat', 1, 0) e
LEFT JOIN inventory_item i ON i.inventory_item_id = e.id
ORDER BY e.id;
