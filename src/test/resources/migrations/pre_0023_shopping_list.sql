-- household, meal_plan and shopping_list exactly as schema.sql had them before migrations/0023_shopping_list_household.sql
-- (for ShoppingListHouseholdMigrationTest). household is a bare stub: only its primary key is needed.

CREATE TABLE household ( household_id INTEGER PRIMARY KEY AUTO_INCREMENT);

CREATE TABLE meal_plan ( meal_plan_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	meal_plan_household_id INTEGER NULL,
	meal_plan_week_start_date DATE NOT NULL,
	UNIQUE INDEX meal_plan_household_week (meal_plan_household_id, meal_plan_week_start_date));

CREATE TABLE shopping_list ( shopping_list_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	shopping_list_meal_plan_id INTEGER,
	shopping_list_created_date DATE NOT NULL,
	shopping_list_shopping_date DATE NULL,
	shopping_list_from_date DATE NULL,
	shopping_list_status BOOLEAN NOT NULL,
	shopping_list_archived BOOLEAN NOT NULL DEFAULT FALSE,
	FOREIGN KEY(shopping_list_meal_plan_id) REFERENCES meal_plan(meal_plan_id));
