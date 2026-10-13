-- Stand-in for migration 0010 (recipe.cuisine) in the tag tests: the column the cuisine tag reads once it exists.
ALTER TABLE recipe ADD COLUMN recipe_cuisine TEXT NULL;
