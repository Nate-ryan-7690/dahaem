-- Archive a shopping list, and free-text shopping list lines (MariaDB).
--
-- Why: every generation adds a list and old ones could not be taken out of the way; and a thing like "Backpapier" could
-- not be put on a list by hand unless it was a catalogue item. Decided by Nate: lists are archived, not deleted (every
-- list stays in the database, so nothing can be lost by a mis-click; it is hidden from the overview and can be brought
-- back), and a line can be plain text.
--
-- What it does:
--   1. shopping_list.shopping_list_archived BOOLEAN NOT NULL DEFAULT FALSE: TRUE = archived (hidden from the overview
--      unless asked for). Every existing list becomes FALSE (not archived).
--   2. shopping_list_item.shopping_list_item_name TEXT NULL: the text of a free-text line (a line with no catalogue
--      item, shopping_list_item_inventory_item_id stays NULL). NULL on every existing line.
-- Nothing is deleted and no existing value is changed.
-- Re-runnable: ADD ... IF NOT EXISTS skips what exists.
-- MariaDB commits every ALTER on its own, so there is no transaction: if a statement fails, fix the cause and run the
-- file again.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0019_shopping_list_archive_and_free_text.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks everything: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

ALTER TABLE shopping_list
    ADD COLUMN IF NOT EXISTS shopping_list_archived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shopping_list_item
    ADD COLUMN IF NOT EXISTS shopping_list_item_name TEXT NULL;

-- The check: every line must say OK.
SELECT 'shopping_list has archived' AS what,
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shopping_list' AND COLUMN_NAME = 'shopping_list_archived') = 1, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'shopping_list_item has name',
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shopping_list_item' AND COLUMN_NAME = 'shopping_list_item_name') = 1, 'OK', 'CHECK')
UNION ALL
SELECT 'no existing list is archived',
       IF((SELECT COUNT(*) FROM shopping_list WHERE shopping_list_archived) = 0, 'OK', 'CHECK');
