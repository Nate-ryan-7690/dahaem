-- household and recipe as stubs before migrations/0026_household_recipe_servings.sql (for
-- HouseholdRecipeServingsMigrationTest): household needs only its primary key, recipe its primary key and the old
-- preference column the migration copies.

CREATE TABLE household ( household_id INTEGER PRIMARY KEY AUTO_INCREMENT);

CREATE TABLE recipe ( recipe_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	recipe_preferred_servings INT NULL);
