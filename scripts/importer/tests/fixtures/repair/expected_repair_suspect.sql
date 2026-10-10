-- Recipe quantity repair, selector 'suspect', generated TEST by scripts/importer/repair_quantities.py
-- Needs migrations/0005_recipe_quantities.sql. Review the .tsv next to this file first.
-- Every statement is guarded on the current value, so a row that changed since the run is left alone
-- and the file can be applied twice. There is no BEGIN/COMMIT on purpose: run it inside ONE transaction
-- after a snapshot (scripts/importer/README.md), e.g.
--   { echo 'START TRANSACTION;'; cat THIS_FILE.sql; echo 'COMMIT;'; } | \
--     docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"'
-- The client stops at the first error, so COMMIT is never reached and nothing is kept.
--
-- selector: suspect    recipes selected: 6    URLs loaded: 8
-- recipes: matched 3 | ambiguous 1 | unmatched 2
-- pages: fetched 0 | from cache 7 | 404 1 | fetch errors 0 | robots.txt-disallowed 0 | not cached (offline) 0
-- rows updated: 7 = ½-bug fixed 3 | rounding fixed 2 | unit changed 1 | now to-taste 1 | other 0
-- lines inserted: 3 = EL/TL 2 | to-taste 1 | other 0
-- servings changed: 1 | servings kept, recipeYield missing 1    source URLs set: 3
-- held for review (TSV only): other changes 1 | uncertain item match 2 | unexplained unit change 1 | unmatched ingredient 0
-- rows unchanged: 3    stored rows not on the page (left as is): 1

-- recipe 1 'Zitronen-Hähnchen' <- https://www.hellofresh.de/recipes/zitronen-hahnchen-5f1a2b3c4d5e6f7a8b9c0d1e
UPDATE recipe SET recipe_source_url = 'https://www.hellofresh.de/recipes/zitronen-hahnchen-5f1a2b3c4d5e6f7a8b9c0d1e' WHERE recipe_id = 1 AND recipe_source_url IS NULL;
UPDATE recipe_ingredient SET recipe_ingredient_quantity_needed = 0.5, recipe_ingredient_unit = 'Stück', recipe_ingredient_to_taste = 0 WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 186 AND recipe_ingredient_quantity_needed <=> 5 AND BINARY recipe_ingredient_unit = 'Stück' AND recipe_ingredient_to_taste = 0;  -- half-bug: ½ Stück Zitrone
UPDATE recipe_ingredient SET recipe_ingredient_quantity_needed = 1.5, recipe_ingredient_unit = 'Stück', recipe_ingredient_to_taste = 0 WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 40 AND recipe_ingredient_quantity_needed <=> 2 AND BINARY recipe_ingredient_unit = 'Stück' AND recipe_ingredient_to_taste = 0;  -- rounding: 1½ Stück Zwiebel
UPDATE recipe_ingredient SET recipe_ingredient_quantity_needed = 0.25, recipe_ingredient_unit = 'Bund', recipe_ingredient_to_taste = 0 WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 125 AND recipe_ingredient_quantity_needed <=> 1 AND BINARY recipe_ingredient_unit = 'Bund' AND recipe_ingredient_to_taste = 0;  -- rounding: ¼ Bund Petersilie
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) SELECT 1, 1022, 'EL', 4, 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredient WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 1022);  -- insert-spoon: 3 Esslöffel Olivenöl
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) SELECT 1, 1070, 'TL', 1, 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredient WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 1070);  -- insert-spoon: 1 Teelöffel Honig
UPDATE recipe_ingredient SET recipe_ingredient_quantity_needed = NULL, recipe_ingredient_unit = '', recipe_ingredient_to_taste = 1 WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 1148 AND recipe_ingredient_quantity_needed <=> 1 AND BINARY recipe_ingredient_unit = 'Stück' AND recipe_ingredient_to_taste = 0;  -- to-taste: nach Geschmack Salz
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) SELECT 1, 1156, '', NULL, 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredient WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 1156);  -- insert-to-taste: Pfeffer

-- recipe 2 'Familien-Bolognese' <- https://www.hellofresh.de/recipes/familien-bolognese-bundle-4p-6a1b2c3d4e5f6a7b8c9d0e1f
UPDATE recipe SET recipe_servings = 4 WHERE recipe_id = 2 AND recipe_servings = 2;
UPDATE recipe SET recipe_source_url = 'https://www.hellofresh.de/recipes/familien-bolognese-bundle-4p-6a1b2c3d4e5f6a7b8c9d0e1f' WHERE recipe_id = 2 AND recipe_source_url IS NULL;
UPDATE recipe_ingredient SET recipe_ingredient_quantity_needed = 0.667, recipe_ingredient_unit = 'Stück', recipe_ingredient_to_taste = 0 WHERE recipe_ingredient_recipe_id = 2 AND recipe_ingredient_item_id = 40 AND recipe_ingredient_quantity_needed <=> 667 AND BINARY recipe_ingredient_unit = 'Stück' AND recipe_ingredient_to_taste = 0;  -- half-bug: ⅔ Stück Zwiebel
UPDATE recipe_ingredient SET recipe_ingredient_quantity_needed = 2, recipe_ingredient_unit = 'Prise', recipe_ingredient_to_taste = 0 WHERE recipe_ingredient_recipe_id = 2 AND recipe_ingredient_item_id = 1061 AND recipe_ingredient_quantity_needed <=> 2 AND BINARY recipe_ingredient_unit = 'Stück' AND recipe_ingredient_to_taste = 0;  -- unit: 2 Prisen Zucker

-- recipe 7 'Kürbissuppe' <- https://www.hellofresh.de/recipes/kurbissuppe-8b1b2c3d4e5f6a7b8c9d0e1f
UPDATE recipe SET recipe_source_url = 'https://www.hellofresh.de/recipes/kurbissuppe-8b1b2c3d4e5f6a7b8c9d0e1f' WHERE recipe_id = 7 AND recipe_source_url IS NULL;
UPDATE recipe_ingredient SET recipe_ingredient_quantity_needed = 0.5, recipe_ingredient_unit = 'Stück', recipe_ingredient_to_taste = 0 WHERE recipe_ingredient_recipe_id = 7 AND recipe_ingredient_item_id = 40 AND recipe_ingredient_quantity_needed <=> 5 AND BINARY recipe_ingredient_unit = 'Stück' AND recipe_ingredient_to_taste = 0;  -- half-bug: ½ Stück Zwiebel
