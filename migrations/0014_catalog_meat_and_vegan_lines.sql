-- Catalogue: items for recipe lines that still had none after the 2026-10-14 rematch (MariaDB). Nate's decisions of
-- 2026-10-14 (follow-up PR), under his item rule (product minus brand; flavours stay one item).
--
-- NEW meat items, each with its animal in inventory_item_animal:
--   1829 Pastrami                          Rind                          (wurst & aufschnitt)
--   1830 Krakauer                          Schwein, Rind, Geflügel POSSIBLE (generic sausage, like Salami)
--   1831 Geflügelwürste                    Geflügel                      (würstchen)
--   1832 Hackfleischzubereitung aus Hähnchen   Hähnchen                  (fresh meat)
-- REUSED, no new item (the catalogue already has the same product); only the Schwein row is added in case
-- ItemAnimalRunner has not set it yet:
--   "Grillhaxe"      -> 267 schweinshaxe
--   "Krustenbraten"  -> 263 schweinebraten
-- NEW vegan substitutes (category plant-based, no animal row, so recipes with them can be vegan again):
--   1824 Veganes Filet nach Lachs-Art      1825 Veganes Pulled in BBQ-Marinade
--   1826 vegane Knusperstäbchen nach Fisch-Art   1827 veganes Schnitzel   1828 veganes Gyros
--   (veganes Schawarma 1613 is a different dish, so Gyros is its own item.)
-- Mapped in scripts/importer/overrides.json, together with: "pflanzliche Margarine" -> 377 margarine and
-- "Hartkäse ital. Art - DONT USE" (a HelloFresh placeholder) -> 414, the item "Hartkäse ital. Art gerieben" uses.
--
-- What it does: it ONLY ADDS (INSERT IGNORE: 9 items, animal rows). Nothing is changed or deleted. Re-runnable.
-- The same rows are in seed/inventory_catalog.sql. Needs migrations 0012 and 0013.
-- The last statement checks everything: every line must say OK. "CHECK" means an id is used by another item on this
-- database or an animal row is missing -- then stop and ask before running the rematch.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with docker-compose.yml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0014_catalog_meat_and_vegan_lines.sql
-- Not applied to the live database by the PR that adds it.

SET NAMES utf8mb4;

INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1824, 'vegan salmon-style filet', 'Veganes Filet nach Lachs-Art', 0, 'g', 'plant-based', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1825, 'vegan pulled (BBQ marinade)', 'Veganes Pulled in BBQ-Marinade', 0, 'g', 'plant-based', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1826, 'vegan fish-style crispy sticks', 'vegane Knusperstäbchen nach Fisch-Art', 0, 'g', 'plant-based', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1827, 'vegan schnitzel', 'veganes Schnitzel', 0, 'g', 'plant-based', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1828, 'vegan gyros', 'veganes Gyros', 0, 'g', 'plant-based', NULL, NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1829, 'pastrami', 'Pastrami', 0, 'g', 'meat', 'wurst & aufschnitt', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1830, 'krakauer sausage', 'Krakauer', 0, 'g', 'meat', 'wurst & aufschnitt', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1831, 'poultry sausages', 'Geflügelwürste', 0, 'g', 'meat', 'würstchen', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1832, 'chicken mince preparation', 'Hackfleischzubereitung aus Hähnchen', 0, 'g', 'meat', 'fresh meat', NULL);

INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1829, 'Rind', 'certain');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1830, 'Schwein', 'possible');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1830, 'Rind', 'possible');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1830, 'Geflügel', 'possible');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1831, 'Geflügel', 'certain');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1832, 'Hähnchen', 'certain');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (267, 'Schwein', 'certain');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (263, 'Schwein', 'certain');

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
FROM (SELECT 1824 AS id, 'new vegan item' AS what, 'Veganes Filet nach Lachs-Art' AS name, 'plant-based' AS category, 0 AS certain_rows, 0 AS possible_rows
      UNION ALL SELECT 1825, 'new vegan item', 'Veganes Pulled in BBQ-Marinade', 'plant-based', 0, 0
      UNION ALL SELECT 1826, 'new vegan item', 'vegane Knusperstäbchen nach Fisch-Art', 'plant-based', 0, 0
      UNION ALL SELECT 1827, 'new vegan item', 'veganes Schnitzel', 'plant-based', 0, 0
      UNION ALL SELECT 1828, 'new vegan item', 'veganes Gyros', 'plant-based', 0, 0
      UNION ALL SELECT 1829, 'new meat item + animal', 'Pastrami', 'meat', 1, 0
      UNION ALL SELECT 1830, 'new meat item + possible animals', 'Krakauer', 'meat', 0, 3
      UNION ALL SELECT 1831, 'new meat item + animal', 'Geflügelwürste', 'meat', 1, 0
      UNION ALL SELECT 1832, 'new meat item + animal', 'Hackfleischzubereitung aus Hähnchen', 'meat', 1, 0
      UNION ALL SELECT 267, 'reused (Grillhaxe), animal', NULL, NULL, 1, 0
      UNION ALL SELECT 263, 'reused (Krustenbraten), animal', NULL, NULL, 1, 0) e
LEFT JOIN inventory_item i ON i.inventory_item_id = e.id
ORDER BY e.id;
