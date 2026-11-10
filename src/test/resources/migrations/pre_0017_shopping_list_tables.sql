-- shopping_list and shopping_list_item exactly as schema.sql had them before migrations/0017_shopping_list_lines.sql
-- (for ShoppingListSchemaMigrationTest). The tables they point at are bare stubs: only their primary key is needed.

CREATE TABLE meal_plan ( meal_plan_id INTEGER PRIMARY KEY AUTO_INCREMENT);
CREATE TABLE recipe ( recipe_id INTEGER PRIMARY KEY AUTO_INCREMENT);
CREATE TABLE store_item ( store_item_id INTEGER PRIMARY KEY AUTO_INCREMENT);
CREATE TABLE inventory_item ( inventory_item_id INTEGER PRIMARY KEY AUTO_INCREMENT);

CREATE TABLE shopping_list ( shopping_list_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	shopping_list_meal_plan_id INTEGER,
	shopping_list_created_date DATE NOT NULL,
	shopping_list_status BOOLEAN NOT NULL,
	FOREIGN KEY(shopping_list_meal_plan_id) REFERENCES meal_plan(meal_plan_id));

CREATE TABLE shopping_list_item(shopping_list_item_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	shopping_list_id INTEGER NOT NULL,
	shopping_list_item_store_item_id INTEGER,
	shopping_list_item_inventory_item_id INTEGER,
	shopping_list_item_store_section TEXT,
	shopping_list_item_quantity_needed INTEGER NOT NULL,
	shopping_list_item_is_discounted BOOLEAN,
	shopping_list_item_purchase_status BOOLEAN NOT NULL,
	shopping_list_item_is_manual BOOLEAN NOT NULL,
	FOREIGN KEY(shopping_list_id) REFERENCES shopping_list(shopping_list_id),
	FOREIGN KEY(shopping_list_item_store_item_id) REFERENCES store_item(store_item_id),
	FOREIGN KEY(shopping_list_item_inventory_item_id) REFERENCES inventory_item(inventory_item_id));
