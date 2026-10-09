-- DAHÄM demo data: fabricated sample data for a one-command demo database.
--
-- Every price, store, brand, meal plan, tag and shopping-list row below is MADE UP for
-- demo purposes. None of it is scraped or pulled from a real store, and it must never be
-- treated as real pricing data. Real store data comes only from the importers in
-- scripts/importer and src/main/java/de/dahaem/stores, run against the live sites.
--
-- Load order: schema.sql -> seed/inventory_catalog.sql ->
--             scripts/recipes/samples/sample_recipes.sql -> this file.
-- INSERT IGNORE so it is safe to re-run against an already-seeded database.
BEGIN;

-- ---------------------------------------------------------------------------------------
-- Stores and store items (fabricated prices/discounts on ingredients the sample recipes use)
-- ---------------------------------------------------------------------------------------

INSERT IGNORE INTO store (store_id, store_name) VALUES
  (1, 'Marktecke Mitte'),
  (2, 'Kornladen Süd');

INSERT IGNORE INTO brand (brand_id, brand_name) VALUES
  (1, 'Hofkorn'),
  (2, 'Talblick');

-- item ids: 40 onion, 45 garlic, 825 tomato passata, 1022 olive oil, 622 long grain rice,
-- 371 cooking cream - all used by multiple sample recipes.
INSERT IGNORE INTO store_item
  (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id,
   store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit)
VALUES
  (1, 1, 1, 40,   'g',  1000, 1.29, 1, NULL),
  (2, 1, 1, 45,   'g',   100, 0.99, 1, NULL),
  (3, 2, 1, 825,  'g',   500, 1.49, 1, NULL),
  (4, 2, 2, 1022, 'ml',  500, 3.99, 1, NULL),
  (5, 1, 2, 622,  'g',  1000, 2.49, 1, NULL),
  (6, 2, 2, 371,  'ml',  200, 0.89, 1, NULL);

-- Current-window discounts, so the shopping list has something on sale right now.
INSERT IGNORE INTO store_discount
  (store_discount_id, store_discount_store_item_id, store_discount_start_date, store_discount_end_date,
   store_discount_price, store_discount_notes)
VALUES
  (1, 1, CURDATE() - INTERVAL 2 DAY, CURDATE() + INTERVAL 5 DAY, 0.99, 'Wochenangebot (demo)'),
  (2, 4, CURDATE() - INTERVAL 1 DAY, CURDATE() + INTERVAL 7 DAY, 3.49, 'Aktionspreis (demo)');

-- ---------------------------------------------------------------------------------------
-- Meal planning: one demo week with a few sample recipes slotted in
-- ---------------------------------------------------------------------------------------

INSERT IGNORE INTO meal_plan (meal_plan_id, meal_plan_week_start_date) VALUES
  (1, CURDATE());

INSERT IGNORE INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, meal_plan_recipe_meal_time)
VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name = 'Cremige Hähnchen-Pilz-Pasta'), 1, 'Abendessen'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name = 'Klassische Rinder-Bolognese'), 1, 'Mittagessen'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name = 'Gemüse-Gnocchi in Tomatensauce'), 1, 'Abendessen');

-- ---------------------------------------------------------------------------------------
-- Tags, applied to a few sample recipes
-- ---------------------------------------------------------------------------------------

INSERT IGNORE INTO tag (tag_id, tag_name, tag_category) VALUES
  (1, 'vegetarisch', 'Diät'),
  (2, 'schnell', 'Zeit'),
  (3, 'familienfreundlich', 'Anlass');

INSERT IGNORE INTO recipe_tag (recipe_tag_recipe_id, recipe_tag_id) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name = 'Gemüse-Gnocchi in Tomatensauce'), 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name = 'Feta-Spinat-Pasta'), 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name = 'Feta-Spinat-Pasta'), 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name = 'Cremige Hähnchen-Pilz-Pasta'), 3),
  ((SELECT recipe_id FROM recipe WHERE recipe_name = 'Zucchini-Mozzarella-Auflauf'), 1);

-- ---------------------------------------------------------------------------------------
-- Shopping list: built from the demo meal plan above, plus one manually added item
-- ---------------------------------------------------------------------------------------

INSERT IGNORE INTO shopping_list (shopping_list_id, shopping_list_meal_plan_id, shopping_list_created_date, shopping_list_status)
VALUES
  (1, 1, CURDATE(), 0);

INSERT IGNORE INTO shopping_list_item
  (shopping_list_item_id, shopping_list_id, shopping_list_item_store_item_id, shopping_list_item_store_section,
   shopping_list_item_quantity_needed, shopping_list_item_is_discounted, shopping_list_item_purchase_status, shopping_list_item_is_manual)
VALUES
  (1, 1, 1, 'Gemüse',    2, 1, 0, 0),  -- onion, currently discounted
  (2, 1, 3, 'Konserven', 1, 0, 0, 0),  -- tomato passata
  (3, 1, 4, 'Öle',       1, 1, 0, 0),  -- olive oil, currently discounted
  (4, 1, 6, 'Kühlregal', 1, 0, 1, 0),  -- cooking cream, already bought
  (5, 1, 2, 'Gemüse',    3, 0, 0, 1);  -- garlic, added manually

COMMIT;
