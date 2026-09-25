-- 15 curated sample recipes for demos (original step text; ingredients reference inventory_item ids).
-- Deliberate ingredient overlap (onion 40, garlic 45, carrot 1, cream 371, passata 825, rice 622, oil 1022, salt 1148, pepper 1156, ...)
-- Pantry staples (oil/salt/pepper/sugar) ARE listed: unlike a meal-kit, our users own and re-buy them.
-- Load into a DB that has the inventory_item catalog.
BEGIN;
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Cremige Hähnchen-Pilz-Pasta', 2, 25, 0, 720, 42, 68, 28, 6, 7);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 241, 'g', 300),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 104, 'g', 250),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 371, 'ml', 200),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 675, 'g', 250),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 414, 'g', 40),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 125, 'Bund', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 1, 'Nudeln in Salzwasser nach Packung bissfest garen, dann abgießen und etwas Kochwasser aufheben.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 2, 'Hähnchen in Streifen schneiden und in Öl rundum anbraten, salzen, pfeffern, herausnehmen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 3, 'Zwiebel, Knoblauch und Pilze im Bratfett weich braten, mit Sahne ablöschen und kurz einköcheln.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cremige Hähnchen-Pilz-Pasta'), 4, 'Hähnchen und Nudeln unterheben, mit Parmesan und gehackter Petersilie abschmecken.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Klassische Rinder-Bolognese', 2, 35, 0, 690, 38, 74, 24, 8, 12);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 270, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 1, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 825, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 675, 'g', 250),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 414, 'g', 40),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 1156, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 1061, 'TL', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 1, 'Zwiebel, Knoblauch und Karotte fein würfeln.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 2, 'Hackfleisch in Öl krümelig anbraten, das Gemüse zugeben und mitrösten.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 3, 'Mit Passata auffüllen, salzen, pfeffern und mindestens 20 Minuten sanft köcheln lassen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Klassische Rinder-Bolognese'), 4, 'Spaghetti bissfest kochen und mit der Sauce und Parmesan servieren.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Gelbes Hähnchen-Curry mit Reis', 2, 30, 0, 650, 40, 72, 20, 7, 10);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 241, 'g', 300),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 35, 'g', 15),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 1557, 'g', 50),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 365, 'ml', 200),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 54, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 622, 'g', 150),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 1156, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 1061, 'TL', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 1, 'Reis nach Packungsangabe garen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 2, 'Zwiebel, Knoblauch und Ingwer in Öl anschwitzen, Currypaste zugeben und kurz mitrösten.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 3, 'Hähnchenwürfel anbraten, Paprika zugeben, mit Kokosmilch ablöschen und cremig einköcheln.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gelbes Hähnchen-Curry mit Reis'), 4, 'Mit Salz, Pfeffer und einer Prise Zucker abschmecken und zum Reis anrichten.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Lachsfilet mit Zitrone und Kartoffeln', 2, 30, 0, 610, 36, 45, 30, 6, 5);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 436, 'g', 300),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 31, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 186, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 371, 'ml', 150),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 8, 'g', 250),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 125, 'Bund', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 1, 'Kartoffeln in Salzwasser garen, Brokkoli die letzten Minuten mitgaren.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 2, 'Lachs mit Zitronensaft beträufeln, salzen und in Öl auf der Hautseite knusprig braten.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 3, 'Sahne mit etwas Zitronenabrieb kurz aufkochen, pfeffern und mit Petersilie verfeinern.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Lachsfilet mit Zitrone und Kartoffeln'), 4, 'Lachs mit Kartoffeln, Brokkoli und der Zitronensahne servieren.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Gemüse-Gnocchi in Tomatensauce', 2, 25, 0, 560, 18, 82, 16, 7, 11);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 1559, 'g', 500),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 825, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 60, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 412, 'g', 125),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 126, 'Bund', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 1, 'Zwiebel und Knoblauch in Öl anschwitzen, Zucchiniwürfel zugeben und anbraten.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 2, 'Mit Passata auffüllen, salzen und pfeffern.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 3, 'Gnocchi kurz in kochendem Wasser garen, bis sie aufsteigen, abgießen und unter die Sauce heben.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Gemüse-Gnocchi in Tomatensauce'), 4, 'Mit Mozzarella belegen, schmelzen lassen und mit Basilikum bestreuen.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Schweinefilet mit Lauch und Kartoffelpüree', 2, 35, 0, 640, 42, 48, 28, 6, 6);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 262, 'g', 300),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 44, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 31, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 371, 'ml', 150),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 1, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 1, 'Kartoffeln weich kochen und mit etwas Sahne zu Püree stampfen, salzen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 2, 'Schweinefilet in Medaillons schneiden, in Öl kräftig anbraten, pfeffern und beiseitestellen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 3, 'Lauch und Karotte in Ringe schneiden und im Bratfett weich dünsten, mit restlicher Sahne binden.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Schweinefilet mit Lauch und Kartoffelpüree'), 4, 'Medaillons zurück in die Pfanne geben und mit dem Püree servieren.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Rote-Linsen-Dal mit Kokos', 2, 25, 0, 520, 22, 74, 14, 12, 8);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 756, 'g', 200),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 35, 'g', 15),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 909, 'g', 40),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 365, 'ml', 200),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 622, 'g', 120),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 1156, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 1061, 'TL', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 1, 'Zwiebel, Knoblauch und Ingwer in Öl anschwitzen und Currypaste zugeben.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 2, 'Rote Linsen einrühren, mit Wasser und Kokosmilch aufgießen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 3, 'Ca. 15 Minuten köcheln, bis die Linsen zerfallen und das Dal cremig wird.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Rote-Linsen-Dal mit Kokos'), 4, 'Mit Salz, Pfeffer und etwas Zucker abschmecken und mit Reis servieren.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Feta-Spinat-Pasta', 2, 20, 0, 640, 24, 78, 24, 8, 8);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 675, 'g', 250),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 92, 'g', 200),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 417, 'g', 150),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 51, 'g', 150),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 1, 'Spaghetti bissfest kochen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 2, 'Zwiebel und Knoblauch in Öl anschwitzen, Kirschtomaten zugeben und andünsten.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 3, 'Spinat zusammenfallen lassen, salzen, pfeffern und mit etwas Nudelwasser cremig rühren.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Feta-Spinat-Pasta'), 4, 'Nudeln unterheben und den zerbröckelten Feta darüberstreuen.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Kichererbsen-Paprika-Eintopf', 2, 30, 0, 480, 20, 66, 12, 16, 14);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 81, 'g', 250),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 54, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 825, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 1, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 1, 'Zwiebel, Knoblauch, Karotte und Paprika würfeln und in Öl anbraten.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 2, 'Kichererbsen und Passata zugeben.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 3, 'Mit Wasser auffüllen und 15 Minuten köcheln lassen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Kichererbsen-Paprika-Eintopf'), 4, 'Kräftig mit Salz und Pfeffer würzen und heiß servieren.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Hähnchen-Gemüse-Wok mit Reis', 2, 25, 0, 600, 38, 70, 16, 8, 9);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 246, 'g', 300),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 54, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 8, 'g', 200),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 1, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 972, 'ml', 30),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 35, 'g', 15),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 622, 'g', 150),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 1, 'Reis garen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 2, 'Hähnchenstreifen in heißem Öl scharf anbraten und herausnehmen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 3, 'Paprika, Brokkoli und Karotte mit Knoblauch und Ingwer knackig braten.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Hähnchen-Gemüse-Wok mit Reis'), 4, 'Hähnchen zurückgeben, mit Sojasauce ablöschen, pfeffern und zum Reis anrichten.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Cheddar-Rinder-Bowl', 2, 25, 0, 780, 40, 60, 40, 7, 8);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 270, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 431, 'g', 100),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 50, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 31, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 1, 'Kartoffeln in Spalten schneiden, mit Öl und Salz mischen und im Ofen goldbraun backen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 2, 'Hackfleisch mit gewürfelter Zwiebel krümelig anbraten, salzen und pfeffern.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 3, 'Cheddar über das heiße Hack schmelzen lassen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Cheddar-Rinder-Bowl'), 4, 'Mit Tomatenwürfeln und den Kartoffelspalten in Schalen anrichten.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Zucchini-Mozzarella-Auflauf', 2, 40, 0, 520, 24, 30, 30, 6, 12);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 60, 'Stück', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 412, 'g', 125),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 825, 'g', 400),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 414, 'g', 40),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 1, 'Zucchini in Scheiben schneiden und in Öl kurz anbraten, salzen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 2, 'Zwiebel und Knoblauch in Passata andünsten und pfeffern.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 3, 'Zucchini und Sauce abwechselnd in eine Form schichten, mit Mozzarella und Parmesan bedecken.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Zucchini-Mozzarella-Auflauf'), 4, 'Bei 200 °C ca. 25 Minuten goldbraun überbacken.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Teriyaki-Lachs mit Reis', 2, 25, 0, 620, 36, 72, 18, 4, 14);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 436, 'g', 300),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 972, 'ml', 40),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 35, 'g', 15),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 8, 'g', 200),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 622, 'g', 150),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 1156, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 1061, 'TL', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 1, 'Reis kochen und Brokkoli darüber dämpfen oder mitgaren.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 2, 'Sojasauce mit Ingwer, Knoblauch und Zucker zu einer Glasur einkochen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 3, 'Lachs in Öl anbraten und mit der Glasur überziehen, bis sie glänzt.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Teriyaki-Lachs mit Reis'), 4, 'Lachs auf Reis mit Brokkoli servieren.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Tomaten-Basilikum-Suppe mit Ciabatta', 2, 25, 0, 420, 10, 52, 18, 7, 16);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 825, 'g', 500),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 40, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 1, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 371, 'ml', 100),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 126, 'Bund', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 562, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 1156, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 1061, 'TL', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 1, 'Zwiebel, Knoblauch und Karotte in Öl anschwitzen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 2, 'Mit Passata und Wasser auffüllen, salzen und 15 Minuten köcheln.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 3, 'Suppe fein pürieren, mit Sahne und einer Prise Zucker verfeinern, Basilikum unterrühren.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Tomaten-Basilikum-Suppe mit Ciabatta'), 4, 'Mit geröstetem Ciabatta servieren.', NULL, 'sample');
INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)
VALUES ('Räuchertofu-Gemüse-Reis', 2, 25, 0, 540, 22, 76, 16, 8, 9);
INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 1594, 'g', 200),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 622, 'g', 150),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 1, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 54, 'Stück', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 972, 'ml', 30),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 45, 'Zehe', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 35, 'g', 15),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 43, 'Bund', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 1022, 'EL', 2),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 1148, 'Prise', 1),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 1156, 'Prise', 1);
INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 1, 'Reis vorkochen und auskühlen lassen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 2, 'Räuchertofu würfeln und in Öl knusprig anbraten, herausnehmen.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 3, 'Karotte und Paprika mit Knoblauch und Ingwer anbraten, Reis zugeben und mitbraten.', NULL, 'sample'),
  ((SELECT recipe_id FROM recipe WHERE recipe_name='Räuchertofu-Gemüse-Reis'), 4, 'Tofu und Sojasauce untermischen, pfeffern und mit Frühlingszwiebeln bestreuen.', NULL, 'sample');
COMMIT;
