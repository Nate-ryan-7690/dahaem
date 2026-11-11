-- The "this meal is always for X people" preference of a recipe belongs to a household (MariaDB).
--
-- Why: last step of separating the data per household (0022 meal plans, 0023 shopping lists, 0024 stock and purchases). The
-- permanent preferred servings of a recipe lived on the shared recipe row (recipe.recipe_preferred_servings), so one
-- household's choice would have changed the other's meals. The recipes themselves stay shared; only the number is per household.
--
-- What it does:
--   1. New table household_recipe_servings (household, recipe, servings, at least 1): one row per household and recipe that
--      has a preference. Every preference set today is copied to the oldest household (the one the first account created).
-- recipe.recipe_preferred_servings is NOT dropped and not touched: the API no longer reads or writes it, it stays as a frozen
-- copy of the preferences at migration time (so the previous API can still start after a rollback). It is dropped later.
-- Re-runnable: CREATE TABLE IF NOT EXISTS skips it when it is there, the copy never overwrites a row that is already there
-- (INSERT IGNORE). MariaDB commits every statement on its own, so there is no transaction.
--
-- ORDER: apply this file BEFORE the API of the same PR starts (the new API needs the table). Needs 0022 to 0024 first.
-- SNAPSHOT FIRST (CLAUDE.md rule 3: this copies live rows), then apply from the PHP project folder (the one with
-- compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0026_household_recipe_servings.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks everything: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

CREATE TABLE IF NOT EXISTS household_recipe_servings (
	household_recipe_servings_household_id INTEGER NOT NULL,
	household_recipe_servings_recipe_id    INTEGER NOT NULL,
	household_recipe_servings_servings     INTEGER NOT NULL,
	PRIMARY KEY (household_recipe_servings_household_id, household_recipe_servings_recipe_id),
	CONSTRAINT household_recipe_servings_at_least_one CHECK (household_recipe_servings_servings >= 1),
	CONSTRAINT household_recipe_servings_household_fk
		FOREIGN KEY (household_recipe_servings_household_id) REFERENCES household(household_id),
	CONSTRAINT household_recipe_servings_recipe_fk
		FOREIGN KEY (household_recipe_servings_recipe_id) REFERENCES recipe(recipe_id));

INSERT IGNORE INTO household_recipe_servings (household_recipe_servings_household_id, household_recipe_servings_recipe_id,
                                              household_recipe_servings_servings)
SELECT (SELECT MIN(household_id) FROM household), r.recipe_id, r.recipe_preferred_servings
  FROM recipe r
 WHERE r.recipe_preferred_servings IS NOT NULL
   AND EXISTS (SELECT 1 FROM household);

-- The check: every line must say OK.
SELECT 'household_recipe_servings exists' AS what,
       IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'household_recipe_servings') = 1, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'every preference has been copied (when a household exists)',
       IF((SELECT COUNT(*) FROM household) = 0
          OR (SELECT COUNT(*) FROM recipe r WHERE r.recipe_preferred_servings IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM household_recipe_servings h
                                WHERE h.household_recipe_servings_recipe_id = r.recipe_id)) = 0, 'OK', 'CHECK')
UNION ALL
SELECT 'the two foreign keys and the check exist',
       IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'household_recipe_servings' AND CONSTRAINT_TYPE IN ('FOREIGN KEY', 'CHECK')) = 3, 'OK', 'CHECK');
