-- How much of each purchase is still in stock (MariaDB).
--
-- Why: stock was one number per item that only ever went up through a purchase, and the item's earliest expiry date
-- only ever moved earlier. Using or throwing away food could not be recorded, and an item's date stayed on a batch that
-- was long gone. With the amount left per purchase, "use 2" can take from the batch that expires first and the item's
-- date can be worked out again from the batches that are left. Decided by Nate: throwing food away is the same action
-- as using it (it leaves stock the same way).
--
-- What it does: purchase_history.purchase_history_quantity_remaining INTEGER NULL. NULL means "nothing used from this
-- purchase yet" (the full quantity purchased is left), so no existing row is rewritten, no backfill is needed, and the
-- API that is running now (which does not know the column) keeps inserting purchases that count as full.
-- Nothing is deleted. Re-runnable: ADD ... IF NOT EXISTS skips what exists.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0018_purchase_quantity_remaining.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks the change: it must say OK. "CHECK" means it did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

ALTER TABLE purchase_history
    ADD COLUMN IF NOT EXISTS purchase_history_quantity_remaining INTEGER NULL
        AFTER purchase_history_quantity_purchased;

-- The check: must say OK.
SELECT 'purchase_history has quantity_remaining' AS what,
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'purchase_history'
           AND COLUMN_NAME = 'purchase_history_quantity_remaining') = 1, 'OK', 'CHECK') AS result;
