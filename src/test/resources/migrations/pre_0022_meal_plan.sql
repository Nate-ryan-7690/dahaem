-- household and meal_plan exactly as schema.sql had them before migrations/0022_meal_plan_household.sql
-- (for HouseholdMealPlanMigrationTest). household is a bare stub: only its primary key is needed.

CREATE TABLE household ( household_id INTEGER PRIMARY KEY AUTO_INCREMENT);

CREATE TABLE meal_plan ( meal_plan_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	meal_plan_week_start_date DATE NOT NULL UNIQUE);
