-- purchase_history exactly as schema.sql had it before migrations/0018_purchase_quantity_remaining.sql
-- (for PurchaseQuantityRemainingMigrationTest). The tables it points at are bare stubs: only their primary key is needed.

CREATE TABLE store_item ( store_item_id INTEGER PRIMARY KEY AUTO_INCREMENT);
CREATE TABLE store_discount ( store_discount_id INTEGER PRIMARY KEY AUTO_INCREMENT);

CREATE TABLE purchase_history(purchase_history_purchase_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	purchase_history_store_item_id INTEGER NOT NULL,
	purchase_history_discount_id INTEGER,
	purchase_history_was_discounted BOOLEAN NOT NULL,
	purchase_history_purchase_date DATE NOT NULL,
	purchase_history_quantity_purchased INTEGER NOT NULL,
	purchase_history_price_paid DECIMAL(10,2) NOT NULL,
	purchase_history_expiration_date DATE NOT NULL,
	FOREIGN KEY(purchase_history_store_item_id) REFERENCES store_item(store_item_id),
	FOREIGN KEY(purchase_history_discount_id) REFERENCES store_discount(store_discount_id));
