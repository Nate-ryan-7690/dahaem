-- The tables migrations/0020_product_barcode.sql points at, as bare stubs: only their primary key is needed
-- (for ProductBarcodeMigrationTest). The barcode tables themselves did not exist before 0020.

CREATE TABLE inventory_item ( inventory_item_id INTEGER PRIMARY KEY AUTO_INCREMENT);
CREATE TABLE brand ( brand_id INTEGER PRIMARY KEY AUTO_INCREMENT);
CREATE TABLE store_item ( store_item_id INTEGER PRIMARY KEY AUTO_INCREMENT);
