-- household, inventory_item and purchase_history exactly as schema.sql had them before migrations/0024_household_stock.sql
-- (for HouseholdStockMigrationTest). household and purchase_history are stubs: only the columns the migration reads or
-- writes are there (purchase_history without its store_item / store_discount foreign keys).

CREATE TABLE household ( household_id INTEGER PRIMARY KEY AUTO_INCREMENT);

CREATE TABLE inventory_item (
	inventory_item_id	INTEGER PRIMARY KEY AUTO_INCREMENT,
	inventory_item_name_english	TEXT NOT NULL,
	inventory_item_quantity	INTEGER NOT NULL DEFAULT 0,
	inventory_item_unit	TEXT NOT NULL,
	inventory_item_category	TEXT NOT NULL,
	inventory_item_earliest_exp_date DATE);

CREATE TABLE purchase_history(purchase_history_purchase_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	purchase_history_store_item_id INTEGER NOT NULL,
	purchase_history_purchase_date DATE NOT NULL,
	purchase_history_price_paid DECIMAL(10,2) NOT NULL);
