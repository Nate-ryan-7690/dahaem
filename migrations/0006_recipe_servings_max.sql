-- Servings ranges: recipe.recipe_servings_max (MariaDB).
--
-- Why: some recipes are written for a range of people ("für 3 – 4 Personen"). recipe_servings
-- stays the number the stored amounts are scaled from (people / servings); for a range it holds the
-- LOWER bound, which is what scaling uses by default. recipe_servings_max keeps the upper bound
-- (4 for "3 – 4 Personen") so that a household can later choose how to read a range. NULL means
-- the recipe is not a range, which is every existing row.
--
-- What it does — it ONLY ADDS, nothing is dropped or rewritten:
--   1. recipe.recipe_servings_max INTEGER NULL (all existing rows: NULL).
--   2. CHECK: a maximum is never below the servings it belongs to. Every existing row has NULL,
--      so it passes.
-- Filling the column is the repair tool's guarded SQL (scripts/importer/repair_quantities.py).
--
-- Re-runnable: ADD ... IF NOT EXISTS skips what exists.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply on llmbox from the PHP project folder:
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0006_recipe_servings_max.sql
-- Not applied to the live database by the PR that adds it.

SET SESSION sql_mode = CONCAT(@@SESSION.sql_mode, ',STRICT_ALL_TABLES');

ALTER TABLE recipe
    ADD COLUMN IF NOT EXISTS recipe_servings_max INTEGER NULL
        AFTER recipe_servings;

ALTER TABLE recipe
    ADD CONSTRAINT IF NOT EXISTS recipe_servings_max_not_below_servings
        CHECK (recipe_servings_max IS NULL OR recipe_servings_max >= recipe_servings);
