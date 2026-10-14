-- Recipe enrichment: description, image links, cuisine, rating and publish date on recipe (MariaDB).
--
-- Why: the recipe pages' JSON-LD holds data the importer never read. Decided by Nate (2026-10-13):
--   - images are LINKED, never copied: only the URLs are stored and the browser loads them from the
--     source. recipe_image_source says where an image comes from, so all of them can be replaced in
--     bulk later (bought licence, own photos, AI-generated): 'hellofresh' | 'own' | 'generated';
--   - the cuisine is stored as one clean German name (config/cuisine_map.json maps the raw values).
--
-- What it does — it ONLY ADDS, nothing is dropped or rewritten:
--   1. recipe.recipe_description TEXT, recipe_image_url TEXT, recipe_thumbnail_url TEXT,
--      recipe_image_source VARCHAR(20), recipe_cuisine VARCHAR(60), recipe_rating_avg DECIMAL(3,2),
--      recipe_rating_count INT, recipe_published_date DATE — all NULL for every existing row.
--   2. CHECK recipe_image_source_valid: NULL, or one of the three values. Every existing row is NULL,
--      so it passes.
-- Filling the columns is NOT done here: scripts/importer/enrich_recipes.py writes a guarded SQL file
-- that is reviewed and applied separately.
--
-- Re-runnable: ADD ... IF NOT EXISTS skips what already exists.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with docker-compose.yml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0010_recipe_enrichment.sql
-- Not applied to the live database by the PR that adds it.

ALTER TABLE recipe
    ADD COLUMN IF NOT EXISTS recipe_description TEXT NULL,
    ADD COLUMN IF NOT EXISTS recipe_image_url TEXT NULL,
    ADD COLUMN IF NOT EXISTS recipe_thumbnail_url TEXT NULL,
    ADD COLUMN IF NOT EXISTS recipe_image_source VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS recipe_cuisine VARCHAR(60) NULL,
    ADD COLUMN IF NOT EXISTS recipe_rating_avg DECIMAL(3,2) NULL,
    ADD COLUMN IF NOT EXISTS recipe_rating_count INT NULL,
    ADD COLUMN IF NOT EXISTS recipe_published_date DATE NULL;

ALTER TABLE recipe
    ADD CONSTRAINT IF NOT EXISTS recipe_image_source_valid
        CHECK (recipe_image_source IS NULL OR recipe_image_source IN ('hellofresh', 'own', 'generated'));
