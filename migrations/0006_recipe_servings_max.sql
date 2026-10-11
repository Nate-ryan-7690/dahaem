-- Recipe servings ranges: recipe.recipe_servings_max for "3 – 4 Personen" recipes (MariaDB).
--
-- Why: some recipes are written for a range of people ("3 – 4 Personen"), and recipe_servings
-- (one INTEGER) could only hold one end of it. Decided by Nate (2026-10-11):
--   - a range "3 – 4 Personen" is stored as recipe_servings = 3 (the lower bound, which stays
--     the default for scaling) and recipe_servings_max = 4; "2 – 4" -> 2 + 4;
--   - an explicit "für 4 Personen" is recipe_servings = 4 with recipe_servings_max NULL;
--   - normal (scraped, 2-person) recipes stay recipe_servings = 2, recipe_servings_max NULL.
--
-- What it does — it ONLY ADDS, nothing is dropped or rewritten:
--   1. recipe.recipe_servings_max INT NULL (every existing row: NULL = not a range).
--   2. CHECK recipe_servings_max_above_servings: NULL, or strictly greater than recipe_servings.
--      Every existing row is NULL, so it passes.
-- Filling the column for range recipes is NOT done here; that is the importer's job
-- (scripts/importer/), reviewed and applied separately.
--
-- Re-runnable: ADD ... IF NOT EXISTS skips what already exists.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply on llmbox from the PHP project folder:
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0006_recipe_servings_max.sql
-- Not applied to the live database by the PR that adds it.

ALTER TABLE recipe
    ADD COLUMN IF NOT EXISTS recipe_servings_max INT NULL
        AFTER recipe_servings;

ALTER TABLE recipe
    ADD CONSTRAINT IF NOT EXISTS recipe_servings_max_above_servings
        CHECK (recipe_servings_max IS NULL OR recipe_servings_max > recipe_servings);
