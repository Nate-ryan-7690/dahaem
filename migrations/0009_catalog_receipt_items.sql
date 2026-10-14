-- Catalogue: four NEW inventory_item rows for receipt names (MariaDB). Nate's decisions of 2026-10-13 on the
-- receipt dry run, under his item rule (product minus brand; flavours stay one item; a different product is
-- its own item). Mapped in config/receipt_overrides.json "exact".
--
--   1818 Gourmetbrötchen       <- "Gourmetbrötchen"   (was matched to 569 brötchen)
--   1819 Rindersalami          <- "RINDERSALAMI"      (was 302 salami; "EDELSALAMI" -> 302 stays)
--   1820 Geflügelfleischwurst  <- "GFL-FLEISCHWURST"  (was 306 fleischwurst)
--   1821 Sour Cream            <- "SOUR CREAM"        (was 368 saure sahne)
-- Unit, category and subcategory are those of the closest sibling item (569, 302, 306, 368).
-- Not added because the catalogue already has the item (reused in the overrides):
--   "Rosinen Brötchen" -> 575 rosinenbrötchen, "Mandarin Orangen" -> 892 mandarinen dose,
--   "Minigurken" -> 59 snackgurke (mini cucumber).
--
-- What it does -- it ONLY ADDS: INSERT IGNORE of four rows; no existing row is changed, nothing is deleted.
-- Re-runnable: a row whose id already exists is skipped. The same rows are in seed/inventory_catalog.sql.
-- The last statement lists the four ids: every row must say OK. "CHECK" means the id was already used by
-- another item on this database -- then stop and ask before running the receipt import.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with docker-compose.yml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0009_catalog_receipt_items.sql
-- Not applied to the live database by the PR that adds it.

INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1818, 'gourmet roll', 'Gourmetbrötchen', 0, 'Stück', 'bread & bakery', 'rolls & buns', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1819, 'beef salami', 'Rindersalami', 0, 'g', 'meat', 'wurst & aufschnitt', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1820, 'poultry fleischwurst', 'Geflügelfleischwurst', 0, 'g', 'meat', 'wurst & aufschnitt', NULL);
INSERT IGNORE INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1821, 'sour cream (thick, American style)', 'Sour Cream', 0, 'g', 'dairy', NULL, NULL);

SELECT e.id, e.name, CASE WHEN i.inventory_item_name_deutsch = e.name THEN 'OK'
                          ELSE CONCAT('CHECK: id is ', COALESCE(i.inventory_item_name_deutsch, 'missing')) END AS result
FROM (SELECT 1818 AS id, 'Gourmetbrötchen' AS name UNION ALL SELECT 1819, 'Rindersalami'
      UNION ALL SELECT 1820, 'Geflügelfleischwurst' UNION ALL SELECT 1821, 'Sour Cream') e
LEFT JOIN inventory_item i ON i.inventory_item_id = e.id
ORDER BY e.id;
