-- Stock and purchases belong to a household (MariaDB).
--
-- Why: third step of separating the data per household (0022 meal plans, 0023 shopping lists). Until now the stock of an
-- item lived on the shared catalogue row (inventory_item.inventory_item_quantity and _earliest_exp_date), so two households
-- would have shared one pantry. Decided by Nate (2026-11-11): the catalogue stays shared, what a household HAS is its own.
--
-- What it does:
--   1. New table household_stock (household, item, quantity, earliest expiry): one row per household and item that has or
--      had stock. Every item with stock today is copied to the oldest household (the one the first account created).
--   2. purchase_history.purchase_history_household_id INTEGER NULL: whose purchase it is (with the batches and the
--      spending figures). Every existing purchase is given to the oldest household. Foreign key to household.
-- The old columns inventory_item.inventory_item_quantity / inventory_item_earliest_exp_date are NOT dropped and not
-- touched: the new API no longer reads or writes them, they stay as a frozen copy of the stock at migration time (so the
-- previous API can still start after a rollback; its stock figures are then the old ones). They are dropped in a later
-- migration. purchase_history_household_id stays NULL-able for now (a later migration makes it NOT NULL).
-- Re-runnable: CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS skip what is done, the copy never overwrites a row that
-- is already there (INSERT IGNORE), the UPDATE only touches purchases without a household, and the foreign key is only added
-- when missing. MariaDB commits every ALTER on its own, so there is no transaction: if a statement fails, fix the cause and
-- run the file again.
--
-- ORDER: apply this file BEFORE the API of the same PR starts (the new API needs household_stock). Needs 0022 and 0023 first.
-- SNAPSHOT FIRST (CLAUDE.md rule 3: this changes live rows), then apply from the PHP project folder (the one with
-- compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0024_household_stock.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks everything: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

CREATE TABLE IF NOT EXISTS household_stock (
	household_stock_household_id      INTEGER NOT NULL,
	household_stock_item_id           INTEGER NOT NULL,
	household_stock_quantity          INTEGER NOT NULL DEFAULT 0,
	household_stock_earliest_exp_date DATE NULL,
	PRIMARY KEY (household_stock_household_id, household_stock_item_id),
	CONSTRAINT household_stock_household_fk
		FOREIGN KEY (household_stock_household_id) REFERENCES household(household_id),
	CONSTRAINT household_stock_item_fk
		FOREIGN KEY (household_stock_item_id) REFERENCES inventory_item(inventory_item_id));

ALTER TABLE purchase_history
    ADD COLUMN IF NOT EXISTS purchase_history_household_id INTEGER NULL;

INSERT IGNORE INTO household_stock (household_stock_household_id, household_stock_item_id, household_stock_quantity,
                                    household_stock_earliest_exp_date)
SELECT (SELECT MIN(household_id) FROM household), ii.inventory_item_id, ii.inventory_item_quantity,
       ii.inventory_item_earliest_exp_date
  FROM inventory_item ii
 WHERE (ii.inventory_item_quantity <> 0 OR ii.inventory_item_earliest_exp_date IS NOT NULL)
   AND EXISTS (SELECT 1 FROM household);

UPDATE purchase_history
   SET purchase_history_household_id = (SELECT MIN(household_id) FROM household)
 WHERE purchase_history_household_id IS NULL;

SET @fk_missing := (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS
                    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_history'
                      AND CONSTRAINT_NAME = 'purchase_history_household_fk');
SET @add_fk := IF(@fk_missing,
    'ALTER TABLE purchase_history ADD CONSTRAINT purchase_history_household_fk FOREIGN KEY (purchase_history_household_id) REFERENCES household(household_id)',
    'SELECT 1');
PREPARE add_fk_statement FROM @add_fk;
EXECUTE add_fk_statement;
DEALLOCATE PREPARE add_fk_statement;

-- The check: every line must say OK.
SELECT 'household_stock exists' AS what,
       IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'household_stock') = 1, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'every item with stock has a stock row (when a household exists)',
       IF((SELECT COUNT(*) FROM household) = 0
          OR (SELECT COUNT(*) FROM inventory_item ii
               WHERE (ii.inventory_item_quantity <> 0 OR ii.inventory_item_earliest_exp_date IS NOT NULL)
                 AND NOT EXISTS (SELECT 1 FROM household_stock hs WHERE hs.household_stock_item_id = ii.inventory_item_id)) = 0,
          'OK', 'CHECK')
UNION ALL
SELECT 'purchase_history has household_id',
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'purchase_history' AND COLUMN_NAME = 'purchase_history_household_id') = 1, 'OK', 'CHECK')
UNION ALL
SELECT 'every purchase has a household (when a household exists)',
       IF((SELECT COUNT(*) FROM purchase_history WHERE purchase_history_household_id IS NULL) = 0
          OR (SELECT COUNT(*) FROM household) = 0, 'OK', 'CHECK')
UNION ALL
SELECT 'the foreign key from purchase_history to household exists',
       IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'purchase_history' AND CONSTRAINT_NAME = 'purchase_history_household_fk') = 1, 'OK', 'CHECK');
