-- Derived recipe tags: inventory_item_animal, recipe_tag.recipe_tag_source (MariaDB).
--
-- Why: recipe tags (Hähnchen, Rind, vegetarisch, schnell, kalorienarm, ...) are to come from DAHÄM's own
-- data, because HelloFresh's data has no usable tags (decided by Nate, 2026-10-13). Nothing in the
-- catalogue records WHICH animal a meat / fish item is, so that gets its own table; and the tagger
-- (de.dahaem.tags.RecipeTagRunner) must be able to tell the tags it derived from the ones a person
-- added by hand, so it only ever replaces its own.
--
-- What it does — it ONLY ADDS, nothing is dropped or rewritten:
--   1. inventory_item_animal (item id, animal), PK = both: an item can have several animals (gemischtes
--      Hackfleisch = Rind + Schwein). CHECK: animal is one of the Tier tags
--      Hähnchen, Pute, Rind, Schwein, Lamm, Kalb, Wild, Fisch, Meeresfrüchte. The table starts EMPTY;
--      it is filled by ItemAnimalRunner --apply, after Nate has reviewed the proposal.
--   2. recipe_tag.recipe_tag_source VARCHAR(10) NOT NULL DEFAULT 'manual', CHECK 'manual' | 'derived'.
--      Every existing tag row becomes 'manual' (so does any tag added through the API), and the tagger
--      never touches a 'manual' row.
--   3. recipe_tag_by_tag index on (recipe_tag_id, recipe_tag_recipe_id), so "all recipes with tag X" is an index lookup.
--
-- Re-runnable: CREATE TABLE IF NOT EXISTS / ADD ... IF NOT EXISTS skip what already exists.
-- SET NAMES makes sure the umlauts in the CHECK list are read as UTF-8 whatever the client default is.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with docker-compose.yml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0011_recipe_tags_derived.sql
-- Not applied to the live database by the PR that adds it.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_item_animal (
    inventory_item_animal_item_id INTEGER NOT NULL,
    inventory_item_animal_animal  VARCHAR(20) NOT NULL,
    PRIMARY KEY (inventory_item_animal_item_id, inventory_item_animal_animal),
    CONSTRAINT inventory_item_animal_is_tier
        CHECK (inventory_item_animal_animal IN
               ('Hähnchen', 'Pute', 'Rind', 'Schwein', 'Lamm', 'Kalb', 'Wild', 'Fisch', 'Meeresfrüchte')),
    FOREIGN KEY (inventory_item_animal_item_id) REFERENCES inventory_item(inventory_item_id));

ALTER TABLE recipe_tag
    ADD COLUMN IF NOT EXISTS recipe_tag_source VARCHAR(10) NOT NULL DEFAULT 'manual';

ALTER TABLE recipe_tag
    ADD CONSTRAINT IF NOT EXISTS recipe_tag_source_valid
        CHECK (recipe_tag_source IN ('manual', 'derived'));

CREATE INDEX IF NOT EXISTS recipe_tag_by_tag ON recipe_tag (recipe_tag_id, recipe_tag_recipe_id);
