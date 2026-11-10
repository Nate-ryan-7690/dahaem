-- Shopping list lines with a real amount, a unit, availability, a review flag and their recipes (MariaDB).
--
-- Why: a saved line was "an integer, in the item's own unit, and only if some store sells it". So 0.5 kg became 1, 500 g
-- and 500 ml looked alike, an ingredient no store sells was silently left off (the list looked complete when it was
-- not), nobody could tell which recipe needed a line, and a list did not say for which day it was made. A line no store
-- sells is now kept (whether any store sells it is worked out when the list is read, not stored). Decided by Nate:
--   - every generated list is its own row and is never rewritten: generating again adds a new list;
--   - a list has a shopping day (today by default, or a later day): offers are judged on that day, so a list made today
--     for a trip on the 17th shows the offers that run on the 17th;
--   - the list counts the meals from a day on (the shopping day by default, the next day when the shopping is after dinner);
--   - stock is subtracted only when its unit is the line's unit and it has not expired; if the units differ nothing is
--     subtracted and the line is flagged for Nate's review.
--
-- What it does:
--   1. shopping_list.shopping_list_shopping_date DATE NULL: the day of the trip, and shopping_list_from_date DATE NULL: the
--      first meal day the list counts (both NULL on a list made before this).
--   2. shopping_list_item.shopping_list_item_quantity_needed INTEGER -> DECIMAL(10,3): every existing amount is kept as is.
--   3. shopping_list_item_unit TEXT NULL: the unit the amount is in (the recipe's unit; NULL on a line made before this,
--      which was in the item's own unit).
--   4. shopping_list_item_needs_review BOOLEAN NOT NULL DEFAULT FALSE: TRUE = the stock was not subtracted because its
--      unit differs from the line's.
--   5. shopping_list_item_recipe: which recipes need a line (a line can come from several).
-- Nothing is deleted and no row is rewritten except the column type in 2.
-- Re-runnable: ADD ... IF NOT EXISTS skips what exists, MODIFY and CREATE TABLE IF NOT EXISTS end in the same state.
-- MariaDB commits every ALTER on its own, so there is no transaction: if a statement fails, fix the cause and run the
-- file again.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0017_shopping_list_lines.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks everything: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

ALTER TABLE shopping_list
    ADD COLUMN IF NOT EXISTS shopping_list_shopping_date DATE NULL
        AFTER shopping_list_created_date;
ALTER TABLE shopping_list
    ADD COLUMN IF NOT EXISTS shopping_list_from_date DATE NULL
        AFTER shopping_list_shopping_date;

ALTER TABLE shopping_list_item
    MODIFY shopping_list_item_quantity_needed DECIMAL(10,3) NOT NULL;
ALTER TABLE shopping_list_item
    ADD COLUMN IF NOT EXISTS shopping_list_item_unit TEXT NULL
        AFTER shopping_list_item_quantity_needed;
ALTER TABLE shopping_list_item
    ADD COLUMN IF NOT EXISTS shopping_list_item_needs_review BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS shopping_list_item_recipe (
    shopping_list_item_recipe_item_id INTEGER NOT NULL,
    shopping_list_item_recipe_recipe_id INTEGER NOT NULL,
    PRIMARY KEY (shopping_list_item_recipe_item_id, shopping_list_item_recipe_recipe_id),
    FOREIGN KEY (shopping_list_item_recipe_item_id) REFERENCES shopping_list_item(shopping_list_item_id),
    FOREIGN KEY (shopping_list_item_recipe_recipe_id) REFERENCES recipe(recipe_id));

-- The check: every line must say OK.
SELECT 'shopping_list has shopping_date and from_date' AS what,
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shopping_list'
           AND COLUMN_NAME IN ('shopping_list_shopping_date', 'shopping_list_from_date')) = 2, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'quantity_needed is DECIMAL(10,3)',
       IF((SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shopping_list_item' AND COLUMN_NAME = 'shopping_list_item_quantity_needed')
          = 'decimal(10,3)', 'OK', 'CHECK')
UNION ALL
SELECT 'shopping_list_item has unit and needs_review',
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shopping_list_item'
           AND COLUMN_NAME IN ('shopping_list_item_unit', 'shopping_list_item_needs_review')) = 2, 'OK', 'CHECK')
UNION ALL
SELECT 'shopping_list_item_recipe exists with both foreign keys',
       IF((SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shopping_list_item_recipe' AND REFERENCED_TABLE_NAME IS NOT NULL) = 2, 'OK', 'CHECK');
