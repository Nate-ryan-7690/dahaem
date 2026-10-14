-- Diet filter: ambiguous animals, alcohol flag, recipe lines the importer could not match (MariaDB).
--
-- Why (decided by Nate, 2026-10-13): a household must be able to say what it does not eat and never
-- have to skip past recipes it cannot eat, WITHOUT the app calling a recipe vegetarian / halal-safe
-- when it is not sure. Three gaps stood in the way:
--   * more animals (Geflügel, Ente, Gans, Kaninchen, Pferd), because rabbit and horse are not kosher,
--     horse is disputed for halal and some people do not eat duck;
--   * generic items (Salami, Fleischwurst, Wiener, Gelatine ...) exist from different animals and the
--     recipe does not say which: they get POSSIBLE animals, which block vegetarisch but give no Tier tag;
--   * recipe_ingredient.recipe_ingredient_item_id is NOT NULL, so the importer DROPPED every ingredient
--     line it could not match. A recipe whose "Speck" line was dropped looked complete and could be
--     tagged vegetarisch. The dropped lines now get a table of their own.
--
-- What it does — it ONLY ADDS; no row is changed, no data is lost:
--   1. inventory_item_animal: the CHECK on the animal is REPLACED by a wider one (the old list plus
--      Geflügel, Ente, Gans, Kaninchen, Pferd). Every row that satisfied the old CHECK satisfies the
--      new one, so replacing it changes no data. New column inventory_item_animal_certainty
--      VARCHAR(10) NOT NULL DEFAULT 'certain', CHECK 'certain' | 'possible': every existing row
--      becomes 'certain', which is what it meant until now.
--   2. inventory_item.inventory_item_alcohol BOOLEAN NOT NULL DEFAULT FALSE: the item contains alcohol
--      as an ingredient (wine, beer, spirits, liqueur, cooking wine ...). Every item starts FALSE; it is
--      set by ItemAnimalRunner --apply after Nate has reviewed the proposal.
--   3. recipe_unmatched_ingredient (id, recipe id, line_no NULL, raw_text, reason 'unmatched' |
--      'unchecked', created_at): one row per recipe line without a catalogue item ('unmatched'), or one
--      row without a line for a recipe whose page could not be checked ('unchecked'). A recipe with ANY
--      row here is "unklar" for diet purposes. The table starts EMPTY; it is filled by the SQL file from
--      scripts/importer/find_unmatched_ingredients.py and, for new imports, by load_recipes.py.
--
-- Re-runnable: ADD ... IF NOT EXISTS / DROP CONSTRAINT IF EXISTS / CREATE TABLE IF NOT EXISTS skip or
-- redo what is already there. SET NAMES makes sure the umlauts in the CHECK list are read as UTF-8
-- whatever the client default is. Needs migration 0011 first.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with docker-compose.yml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0012_diet_filter.sql
-- Not applied to the live database by the PR that adds it.

SET NAMES utf8mb4;

ALTER TABLE inventory_item_animal DROP CONSTRAINT IF EXISTS inventory_item_animal_is_tier;

ALTER TABLE inventory_item_animal
    ADD CONSTRAINT inventory_item_animal_is_tier
        CHECK (inventory_item_animal_animal IN
               ('Hähnchen', 'Pute', 'Geflügel', 'Ente', 'Gans', 'Rind', 'Schwein', 'Lamm', 'Kalb', 'Wild',
                'Kaninchen', 'Pferd', 'Fisch', 'Meeresfrüchte'));

ALTER TABLE inventory_item_animal
    ADD COLUMN IF NOT EXISTS inventory_item_animal_certainty VARCHAR(10) NOT NULL DEFAULT 'certain';

ALTER TABLE inventory_item_animal
    ADD CONSTRAINT IF NOT EXISTS inventory_item_animal_certainty_valid
        CHECK (inventory_item_animal_certainty IN ('certain', 'possible'));

ALTER TABLE inventory_item
    ADD COLUMN IF NOT EXISTS inventory_item_alcohol BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS recipe_unmatched_ingredient (
    recipe_unmatched_ingredient_id        INTEGER PRIMARY KEY AUTO_INCREMENT,
    recipe_unmatched_ingredient_recipe_id INTEGER NOT NULL,
    recipe_unmatched_ingredient_line_no   INTEGER NULL,
    recipe_unmatched_ingredient_raw_text  VARCHAR(500) NOT NULL,
    recipe_unmatched_ingredient_reason    VARCHAR(10) NOT NULL,
    recipe_unmatched_ingredient_created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT recipe_unmatched_ingredient_reason_valid
        CHECK (recipe_unmatched_ingredient_reason IN ('unmatched', 'unchecked')),
    FOREIGN KEY (recipe_unmatched_ingredient_recipe_id) REFERENCES recipe(recipe_id));

CREATE INDEX IF NOT EXISTS recipe_unmatched_ingredient_by_recipe
    ON recipe_unmatched_ingredient (recipe_unmatched_ingredient_recipe_id);
