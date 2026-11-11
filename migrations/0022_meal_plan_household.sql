-- Meal plans belong to a household (MariaDB).
--
-- Why: until now nothing but the household and its members knew about households, so every meal plan was shared by
-- everyone. For the alpha test with two households each household needs its own plans, and the same week must be
-- plannable by both. Decided by Nate (2026-11-11): the household of a request comes from the logged-in user, data is
-- separated per household, the catalogue (recipes, items, stores, prices, barcodes) stays shared. This is the first
-- step: the meal plans. Shopping lists, stock, purchases and receipts follow in later migrations.
--
-- What it does:
--   1. meal_plan.meal_plan_household_id INTEGER NULL: whose plan it is. Every existing plan is given to the oldest
--      household (the one the first account created); nothing is deleted.
--   2. The week was UNIQUE on its own: now it is unique per household (meal_plan_household_week), the old
--      one-plan-per-week index meal_plan_week_start_date is dropped.
--   3. A foreign key to household.
-- The column stays NULL-able for now (a later migration makes it NOT NULL once everything runs with it).
-- Re-runnable: ADD ... IF NOT EXISTS / DROP INDEX IF EXISTS skip what is done, the foreign key is only added when missing,
-- and the UPDATE only touches plans without a household. MariaDB commits every ALTER on its own, so there is no
-- transaction: if a statement fails, fix the cause and run the file again.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3: this changes live rows), then apply from the PHP project folder (the one with
-- compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0022_meal_plan_household.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks everything: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it. Deploy the API of the same PR right after it: the new API needs
-- the column, and the old API does not know it (it keeps working with it present, but a second household would see
-- the first one's plans).

ALTER TABLE meal_plan
    ADD COLUMN IF NOT EXISTS meal_plan_household_id INTEGER NULL;

UPDATE meal_plan
   SET meal_plan_household_id = (SELECT MIN(household_id) FROM household)
 WHERE meal_plan_household_id IS NULL;

ALTER TABLE meal_plan
    ADD UNIQUE INDEX IF NOT EXISTS meal_plan_household_week (meal_plan_household_id, meal_plan_week_start_date);

SET @fk_missing := (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS
                    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan'
                      AND CONSTRAINT_NAME = 'meal_plan_household_fk');
SET @add_fk := IF(@fk_missing,
    'ALTER TABLE meal_plan ADD CONSTRAINT meal_plan_household_fk FOREIGN KEY (meal_plan_household_id) REFERENCES household(household_id)',
    'SELECT 1');
PREPARE add_fk_statement FROM @add_fk;
EXECUTE add_fk_statement;
DEALLOCATE PREPARE add_fk_statement;

ALTER TABLE meal_plan
    DROP INDEX IF EXISTS meal_plan_week_start_date;

-- The check: every line must say OK.
SELECT 'meal_plan has household_id' AS what,
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'meal_plan' AND COLUMN_NAME = 'meal_plan_household_id') = 1, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'every plan has a household (when a household exists)',
       IF((SELECT COUNT(*) FROM meal_plan WHERE meal_plan_household_id IS NULL) = 0
          OR (SELECT COUNT(*) FROM household) = 0, 'OK', 'CHECK')
UNION ALL
SELECT 'the foreign key to household exists',
       IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'meal_plan' AND CONSTRAINT_NAME = 'meal_plan_household_fk') = 1, 'OK', 'CHECK')
UNION ALL
SELECT 'one plan per household and week',
       IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'meal_plan' AND INDEX_NAME = 'meal_plan_household_week' AND NON_UNIQUE = 0) = 2, 'OK', 'CHECK')
UNION ALL
SELECT 'the old one-plan-per-week index is gone',
       IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'meal_plan' AND INDEX_NAME = 'meal_plan_week_start_date') = 0, 'OK', 'CHECK');
