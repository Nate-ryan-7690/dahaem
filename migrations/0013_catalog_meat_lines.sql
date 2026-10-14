-- Catalogue: three meat items for recipe lines that had none, and two category corrections (MariaDB).
-- Nate's decisions of 2026-10-14 after the unmatched-lines run (docs/BACKLOG.md, "Catalogue batch 2").
--
-- 1. NEW items, product-minus-brand rule, each with its animal (CERTAIN) in inventory_item_animal.
--    I checked the catalogue first. Rinderkeulenstreifen is NOT new: 1660 Rindergeschnetzeltes (beef strips) is
--    the item overrides.json has mapped "rinderkeulenstreifen" to since the first import, so it is reused; only its
--    animal row is added (below), in case ItemAnimalRunner has not set it yet. There is a Schweinebraten 263 and a
--    Rehkeule 298, but no deer steak and no cold-cut pork roast, so these two are new. Unit g, category meat; the
--    subcategory is that of the closest sibling.
--      1822 Hirschsteak                   Wild     (fresh meat, like 298 Rehkeule)
--      1823 Schweinsbraten-Aufschnitt     Schwein  (wurst & aufschnitt, like 325 Roastbeef Aufschnitt)
--      1660 Rindergeschnetzeltes (reused) Rind     (animal row only)
--    Mapped in scripts/importer/overrides.json so the importer and scripts/importer/rematch_unmatched.py find them.
--
-- 2. CORRECTIONS: two vegetarian products sat in 'canned & jarred goods' / 'canned meat', which the tagger reads as
--    meat, so every recipe with them was kept out of vegetarisch. Only the subcategory changes, guarded on the id
--    AND the current value (a row someone already moved is left alone):
--      877 chili sin carne dose  canned meat -> canned legumes     (beans and maize; siblings 844 baked beans,
--                                                                   840 kidneybohnen dose)
--      887 gemüsetopf dose       canned meat -> canned vegetables  (vegetable stew; siblings 838 mischgemüse dose,
--                                                                   837 letscho dose)
--    There is no 'ready meal' subcategory in the catalogue, so these two use the nearest canned-food subcategory
--    by main ingredient. The other canned stews (Erbseneintopf, Linseneintopf, Bohneneintopf, Kartoffeltopf ...)
--    stay in 'canned meat': they are not part of this decision and may contain meat.
--
-- What it does: INSERT IGNORE of 2 items + 3 animal rows and two guarded UPDATEs. Nothing is deleted; no other row is changed.
-- Re-runnable: an existing id / (item, animal) pair is skipped; an UPDATE that no longer matches changes nothing.
-- The same rows are in seed/inventory_catalog.sql. Needs migration 0012 (inventory_item_animal_certainty).
-- The last statement checks everything: every line must say OK. "CHECK" means the id is used by another item on
-- this database, an animal row is missing, or a correction did not apply -- then stop and ask before going on.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply on llmbox from the PHP project folder:
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0013_catalog_meat_lines.sql
-- Not applied to the live database by the PR that adds it.

SET NAMES utf8mb4;

INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1822, 'venison steak', 'Hirschsteak', 0, 'g', 'meat', 'fresh meat', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1823, 'sliced roast pork', 'Schweinsbraten-Aufschnitt', 0, 'g', 'meat', 'wurst & aufschnitt', NULL);

INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1660, 'Rind', 'certain');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1822, 'Wild', 'certain');
INSERT IGNORE INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, inventory_item_animal_certainty) VALUES (1823, 'Schwein', 'certain');

UPDATE inventory_item SET inventory_item_subcategory = 'canned legumes' WHERE inventory_item_id = 877 AND inventory_item_name_deutsch = 'chili sin carne dose' AND inventory_item_category = 'canned & jarred goods' AND inventory_item_subcategory = 'canned meat';
UPDATE inventory_item SET inventory_item_subcategory = 'canned vegetables' WHERE inventory_item_id = 887 AND inventory_item_name_deutsch = 'gemüsetopf dose' AND inventory_item_category = 'canned & jarred goods' AND inventory_item_subcategory = 'canned meat';

SELECT e.what, CASE WHEN i.inventory_item_name_deutsch = e.name AND i.inventory_item_category = e.category
                         AND i.inventory_item_subcategory <=> e.subcategory
                         AND (SELECT COUNT(*) FROM inventory_item_animal a
                              WHERE a.inventory_item_animal_item_id = e.id
                                AND a.inventory_item_animal_animal = e.animal
                                AND a.inventory_item_animal_certainty = 'certain') = e.animal_rows
                    THEN 'OK'
                    ELSE CONCAT('CHECK: id is ', COALESCE(i.inventory_item_name_deutsch, 'missing'), ' / ',
                                COALESCE(i.inventory_item_category, '-'), ' / ', COALESCE(i.inventory_item_subcategory, '-'))
               END AS result
FROM (SELECT 1660 AS id, 'reused item, animal' AS what, 'Rindergeschnetzeltes' AS name, 'meat & poultry' AS category,
             NULL AS subcategory, 'Rind' AS animal, 1 AS animal_rows
      UNION ALL SELECT 1822, 'new item + animal', 'Hirschsteak', 'meat', 'fresh meat', 'Wild', 1
      UNION ALL SELECT 1823, 'new item + animal', 'Schweinsbraten-Aufschnitt', 'meat', 'wurst & aufschnitt',
                        'Schwein', 1
      UNION ALL SELECT 877, 'correction', 'chili sin carne dose', 'canned & jarred goods', 'canned legumes', '', 0
      UNION ALL SELECT 887, 'correction', 'gemüsetopf dose', 'canned & jarred goods', 'canned vegetables', '', 0) e
LEFT JOIN inventory_item i ON i.inventory_item_id = e.id
ORDER BY e.id;
