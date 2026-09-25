-- Adds the inventory_item link to shopping_list_item, so a persisted line records WHICH item
-- it is for and not just how many are still needed.
--
-- Only needed for a database created before this migration. A fresh database built from
-- schema.sql (e.g. via setup_db.py) already has this column.
--
-- SQLite cannot add a FOREIGN KEY constraint via ALTER TABLE ADD COLUMN, so this upgrades an
-- existing file with a plain, unconstrained column; the FK is only enforced for tables created
-- from schema.sql going forward. The application still writes a valid inventory_item_id on every
-- generated line, so an upgraded database stays consistent as long as it is written through the
-- app.
--
-- Apply with:
--   sqlite3 data/kitchen_organizer.db < migrations/0001_add_shopping_list_item_inventory_item_id.sql

ALTER TABLE shopping_list_item ADD COLUMN shopping_list_item_inventory_item_id INTEGER;
