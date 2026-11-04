-- household, recipe, meal_plan and meal_plan_recipe exactly as schema.sql had them before migrations/0016_meal_plan_entries.sql (for MealPlanSchemaMigrationTest).

CREATE TABLE household ( household_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	household_name TEXT NOT NULL,
	created_date DATE NOT NULL);

CREATE TABLE recipe ( recipe_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	recipe_name TEXT NOT NULL,

	recipe_servings INTEGER NOT NULL,

	recipe_servings_max INT NULL,

	recipe_cook_time INTEGER NOT NULL,

	recipe_prep_time INTEGER NOT NULL,

	recipe_calories INTEGER,

	recipe_protein INTEGER,

	recipe_carbs INTEGER,

	recipe_fat INTEGER,

	recipe_fibre INTEGER,

	recipe_sugar INTEGER,

	recipe_source_url TEXT,

	recipe_description TEXT NULL,

	recipe_image_url TEXT NULL,

	recipe_thumbnail_url TEXT NULL,

	recipe_image_source VARCHAR(20) NULL,

	recipe_cuisine VARCHAR(60) NULL,

	recipe_rating_avg DECIMAL(3,2) NULL,

	recipe_rating_count INT NULL,

	recipe_published_date DATE NULL,

	CONSTRAINT recipe_image_source_valid
		CHECK (recipe_image_source IS NULL OR recipe_image_source IN ('hellofresh', 'own', 'generated')),

	CONSTRAINT recipe_servings_max_above_servings
		CHECK (recipe_servings_max IS NULL OR recipe_servings_max > recipe_servings));

CREATE TABLE meal_plan ( meal_plan_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	meal_plan_week_start_date DATE NOT NULL UNIQUE);

CREATE TABLE meal_plan_recipe(meal_plan_recipe_id INTEGER,

	meal_plan_recipe_plan_id INTEGER,

	meal_plan_recipe_meal_time TEXT,

	PRIMARY KEY(meal_plan_recipe_id,meal_plan_recipe_plan_id),

	FOREIGN KEY(meal_plan_recipe_id) REFERENCES recipe(recipe_id),

	FOREIGN KEY(meal_plan_recipe_plan_id) REFERENCES meal_plan(meal_plan_id));
