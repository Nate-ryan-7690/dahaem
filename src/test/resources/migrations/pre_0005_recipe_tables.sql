-- recipe tables exactly as schema.sql had them before migrations/0005_recipe_quantities.sql (for MigrationTest).

CREATE TABLE inventory_item (
	inventory_item_id	INTEGER PRIMARY KEY AUTO_INCREMENT,
	inventory_item_name_english	TEXT NOT NULL,
	inventory_item_name_deutsch	TEXT,
	inventory_item_quantity	INTEGER NOT NULL DEFAULT 0,
	inventory_item_unit	TEXT NOT NULL,
	inventory_item_category	TEXT NOT NULL,
	inventory_item_subcategory	TEXT,
	inventory_item_earliest_exp_date DATE);

CREATE TABLE recipe ( recipe_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	recipe_name TEXT NOT NULL,

	recipe_servings INTEGER NOT NULL,

	recipe_cook_time INTEGER NOT NULL,

	recipe_prep_time INTEGER NOT NULL,

	recipe_calories INTEGER,

	recipe_protein INTEGER,

	recipe_carbs INTEGER,

	recipe_fat INTEGER,

	recipe_fibre INTEGER,

	recipe_sugar INTEGER);

CREATE TABLE recipe_ingredient (recipe_ingredient_item_id INTEGER,

	recipe_ingredient_recipe_id INTEGER NOT NULL,

	recipe_ingredient_unit TEXT NOT NULL,

	recipe_ingredient_quantity_needed INTEGER NOT NULL,

	PRIMARY KEY(recipe_ingredient_item_id,recipe_ingredient_recipe_id),

	FOREIGN KEY(recipe_ingredient_item_id) REFERENCES inventory_item(inventory_item_id),

	FOREIGN KEY(recipe_ingredient_recipe_id) REFERENCES recipe(recipe_id));
