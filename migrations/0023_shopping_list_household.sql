-- Shopping lists belong to a household (MariaDB).
--
-- Why: second step of separating the data per household (the first, 0022, did the meal plans). Until now every shopping
-- list was visible to everyone. Decided by Nate (2026-11-11): the household of a request comes from the logged-in user.
--
-- What it does:
--   1. shopping_list.shopping_list_household_id INTEGER NULL: whose list it is. A list made from a meal plan is given to the
--      household of that plan; every other list (no plan) to the oldest household. Nothing is deleted.
--   2. A foreign key to household.
-- The column stays NULL-able for now (a later migration makes it NOT NULL once everything runs with it).
-- Needs migration 0022 first (it reads meal_plan.meal_plan_household_id).
-- Re-runnable: ADD COLUMN IF NOT EXISTS skips what is done, the foreign key is only added when missing, and the UPDATEs only
-- touch lists without a household. MariaDB commits every ALTER on its own, so there is no transaction: if a statement
-- fails, fix the cause and run the file again.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3: this changes live rows), then apply from the PHP project folder (the one with
-- compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0023_shopping_list_household.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks everything: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it. Deploy the API of the same PR right after it.

ALTER TABLE shopping_list
    ADD COLUMN IF NOT EXISTS shopping_list_household_id INTEGER NULL;

UPDATE shopping_list sl
  JOIN meal_plan mp ON mp.meal_plan_id = sl.shopping_list_meal_plan_id
   SET sl.shopping_list_household_id = mp.meal_plan_household_id
 WHERE sl.shopping_list_household_id IS NULL;

UPDATE shopping_list
   SET shopping_list_household_id = (SELECT MIN(household_id) FROM household)
 WHERE shopping_list_household_id IS NULL;

SET @fk_missing := (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS
                    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list'
                      AND CONSTRAINT_NAME = 'shopping_list_household_fk');
SET @add_fk := IF(@fk_missing,
    'ALTER TABLE shopping_list ADD CONSTRAINT shopping_list_household_fk FOREIGN KEY (shopping_list_household_id) REFERENCES household(household_id)',
    'SELECT 1');
PREPARE add_fk_statement FROM @add_fk;
EXECUTE add_fk_statement;
DEALLOCATE PREPARE add_fk_statement;

-- The check: every line must say OK.
SELECT 'shopping_list has household_id' AS what,
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shopping_list' AND COLUMN_NAME = 'shopping_list_household_id') = 1, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'every list has a household (when a household exists)',
       IF((SELECT COUNT(*) FROM shopping_list WHERE shopping_list_household_id IS NULL) = 0
          OR (SELECT COUNT(*) FROM household) = 0, 'OK', 'CHECK')
UNION ALL
SELECT 'a list with a plan belongs to the household of that plan',
       IF((SELECT COUNT(*) FROM shopping_list sl JOIN meal_plan mp ON mp.meal_plan_id = sl.shopping_list_meal_plan_id
           WHERE NOT (sl.shopping_list_household_id <=> mp.meal_plan_household_id)) = 0, 'OK', 'CHECK')
UNION ALL
SELECT 'the foreign key to household exists',
       IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shopping_list' AND CONSTRAINT_NAME = 'shopping_list_household_fk') = 1, 'OK', 'CHECK');
