-- Recipe quantities: decimal amounts, "nach Geschmack" ingredients, recipe source URL (MariaDB).
--
-- Why: recipe_ingredient_quantity_needed was INTEGER, so "½ Stück Zitrone" could not be stored
-- (the old importer turned it into 5 or rounded it to 1), and ingredients without an amount
-- ("Salz nach Geschmack") had no honest representation. recipe had no link back to its page.
--
-- What it does — it ONLY WIDENS AND ADDS, nothing is dropped or rewritten:
--   1. recipe_ingredient_quantity_needed  INTEGER NOT NULL -> DECIMAL(10,3) NULL
--      (every existing integer value is kept exactly: 5 -> 5.000).
--   2. recipe_ingredient_to_taste BOOLEAN NOT NULL DEFAULT FALSE (all existing rows: FALSE).
--   3. CHECK: a row has either a quantity, or to_taste = TRUE (never both, never neither).
--      Every existing row has a quantity and to_taste FALSE, so it passes.
--   4. recipe.recipe_source_url TEXT NULL, so later refreshes can match a recipe 1:1 to its page.
-- Fixing the wrong values themselves is NOT done here; that is the repair tool's guarded SQL
-- (scripts/importer/repair_quantities.py), reviewed and applied separately.
--
-- Re-runnable: MODIFY to the same type is a no-op, ADD ... IF NOT EXISTS skips what exists.
-- Strict mode is forced for this session so a value that does not fit errors instead of being
-- silently clipped.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with docker-compose.yml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0005_recipe_quantities.sql
-- Not applied to the live database by the PR that adds it.

SET SESSION sql_mode = CONCAT(@@SESSION.sql_mode, ',STRICT_ALL_TABLES');

ALTER TABLE recipe_ingredient
    MODIFY COLUMN recipe_ingredient_quantity_needed DECIMAL(10,3) NULL;

ALTER TABLE recipe_ingredient
    ADD COLUMN IF NOT EXISTS recipe_ingredient_to_taste BOOLEAN NOT NULL DEFAULT FALSE
        AFTER recipe_ingredient_quantity_needed;

ALTER TABLE recipe_ingredient
    ADD CONSTRAINT IF NOT EXISTS recipe_ingredient_quantity_or_to_taste
        CHECK ((recipe_ingredient_quantity_needed IS NULL) = (recipe_ingredient_to_taste <> 0));

ALTER TABLE recipe
    ADD COLUMN IF NOT EXISTS recipe_source_url TEXT NULL;
