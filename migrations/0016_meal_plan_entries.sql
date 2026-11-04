-- Meal plans with dated, repeatable meals and per-meal servings (MariaDB). Nate's design of 2026-11-04.
--
-- Why: a planned meal needs its own identity, a day, an order and a head count. The table meal_plan_recipe is keyed by
-- (recipe, plan), so a recipe could be in a plan once (a second add was a duplicate-key error, the 409 of the API),
-- it had no date and no servings. Decided by Nate:
--   - the same recipe may be planned as often as wanted: every planned meal gets its own id;
--   - a plan stays one Monday-to-Sunday week (meal_plan.meal_plan_week_start_date); each meal carries its own date;
--   - servings are per meal and nullable: NULL = follow the default (the recipe's permanent preferred number
--     recipe.recipe_preferred_servings, then the household's head count household.household_people, then 2);
--   - the existing meal_plan_recipe_meal_time becomes the free label ("Frühstück", "Snack" ...), no fixed slots;
--   - no archive table: a past meal is simply an old row; a nightly job (not in this migration) writes the resolved
--     servings onto yesterday's rows so they stop following the household number.
--
-- What it does:
--   1. household.household_people INT NULL (the household's head count; NULL = not set, so the default 2 applies),
--      CHECK household_people_at_least_one.
--   2. recipe.recipe_preferred_servings INT NULL ("this meal is always for X people"; NULL = no preference; it never
--      changes recipe_servings, the number the stored amounts are written for), CHECK recipe_preferred_servings_at_least_one.
--   3. meal_plan_recipe, changed in place:
--        meal_plan_recipe_entry_id  INT AUTO_INCREMENT, the NEW PRIMARY KEY (existing rows are numbered 1, 2, 3 ...)
--        meal_plan_recipe_id        the recipe  (stays a FOREIGN KEY to recipe, no longer part of the primary key)
--        meal_plan_recipe_plan_id   the plan    (stays a FOREIGN KEY to meal_plan, no longer part of the primary key)
--        meal_plan_recipe_date      DATE NULL           the day of the meal (NULL = a row made by the old endpoint)
--        meal_plan_recipe_meal_time TEXT                the free label (unchanged column)
--        meal_plan_recipe_servings  INT NULL            CHECK >= 1; NULL = follow the default
--        meal_plan_recipe_position  INT NOT NULL DEFAULT 0   the order within the day
--        meal_plan_recipe_note      TEXT NULL           a free note
--      plus indexes on the recipe, on the plan, and on (plan, date). The two indexes on the foreign key columns are
--      added FIRST, because the foreign keys use the old primary key as their index and MariaDB refuses to drop it
--      otherwise.
--
-- Nothing is deleted and no row is rewritten. Every existing row keeps its recipe, plan and label and only gets an id.
-- One behaviour change for the old endpoints: adding the same recipe to a plan twice no longer fails with a 409, it
-- adds a second meal. That is the decision above. The old endpoints otherwise keep working: their INSERT names its
-- columns, and every new column is nullable or has a default.
-- Re-runnable: ADD ... IF NOT EXISTS skips what already exists, and the primary key swap is rebuilt to the same state.
-- MariaDB commits every ALTER on its own, so there is no transaction: if a statement fails, fix the cause and run the
-- file again.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0016_meal_plan_entries.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks everything: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

-- 1. household head count
ALTER TABLE household
    ADD COLUMN IF NOT EXISTS household_people INT NULL
        AFTER household_name;
ALTER TABLE household
    ADD CONSTRAINT IF NOT EXISTS household_people_at_least_one
        CHECK (household_people IS NULL OR household_people >= 1);

-- 2. the recipe's permanent preferred servings
ALTER TABLE recipe
    ADD COLUMN IF NOT EXISTS recipe_preferred_servings INT NULL
        AFTER recipe_servings_max;
ALTER TABLE recipe
    ADD CONSTRAINT IF NOT EXISTS recipe_preferred_servings_at_least_one
        CHECK (recipe_preferred_servings IS NULL OR recipe_preferred_servings >= 1);

-- 3. meal_plan_recipe: indexes for the foreign keys first, then the new primary key
ALTER TABLE meal_plan_recipe
    ADD INDEX IF NOT EXISTS meal_plan_recipe_by_recipe (meal_plan_recipe_id);
ALTER TABLE meal_plan_recipe
    ADD INDEX IF NOT EXISTS meal_plan_recipe_by_plan (meal_plan_recipe_plan_id);
ALTER TABLE meal_plan_recipe
    ADD COLUMN IF NOT EXISTS meal_plan_recipe_entry_id INT NOT NULL AUTO_INCREMENT FIRST,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (meal_plan_recipe_entry_id);
ALTER TABLE meal_plan_recipe
    MODIFY meal_plan_recipe_id INT NOT NULL,
    MODIFY meal_plan_recipe_plan_id INT NOT NULL;
ALTER TABLE meal_plan_recipe
    ADD COLUMN IF NOT EXISTS meal_plan_recipe_date DATE NULL
        AFTER meal_plan_recipe_plan_id;
ALTER TABLE meal_plan_recipe
    ADD COLUMN IF NOT EXISTS meal_plan_recipe_servings INT NULL
        AFTER meal_plan_recipe_meal_time;
ALTER TABLE meal_plan_recipe
    ADD COLUMN IF NOT EXISTS meal_plan_recipe_position INT NOT NULL DEFAULT 0
        AFTER meal_plan_recipe_servings;
ALTER TABLE meal_plan_recipe
    ADD COLUMN IF NOT EXISTS meal_plan_recipe_note TEXT NULL
        AFTER meal_plan_recipe_position;
ALTER TABLE meal_plan_recipe
    ADD INDEX IF NOT EXISTS meal_plan_recipe_by_plan_date (meal_plan_recipe_plan_id, meal_plan_recipe_date);
ALTER TABLE meal_plan_recipe
    ADD CONSTRAINT IF NOT EXISTS meal_plan_recipe_servings_at_least_one
        CHECK (meal_plan_recipe_servings IS NULL OR meal_plan_recipe_servings >= 1);

-- The check: every line must say OK.
SELECT 'household.household_people exists' AS what,
       IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'household' AND COLUMN_NAME = 'household_people'), 'OK', 'CHECK') AS result
UNION ALL
SELECT 'recipe.recipe_preferred_servings exists',
       IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'recipe' AND COLUMN_NAME = 'recipe_preferred_servings'), 'OK', 'CHECK')
UNION ALL
SELECT 'meal_plan_recipe has the five new columns',
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'meal_plan_recipe'
           AND COLUMN_NAME IN ('meal_plan_recipe_entry_id', 'meal_plan_recipe_date', 'meal_plan_recipe_servings',
                               'meal_plan_recipe_position', 'meal_plan_recipe_note')) = 5, 'OK', 'CHECK')
UNION ALL
SELECT 'meal_plan_recipe primary key is the entry id only',
       IF((SELECT GROUP_CONCAT(COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_recipe'
           AND CONSTRAINT_NAME = 'PRIMARY') = 'meal_plan_recipe_entry_id', 'OK', 'CHECK')
UNION ALL
SELECT 'meal_plan_recipe still has both foreign keys',
       IF((SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_recipe'
           AND REFERENCED_TABLE_NAME IN ('recipe', 'meal_plan')) = 2, 'OK', 'CHECK');
