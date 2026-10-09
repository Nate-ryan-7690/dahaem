-- Item-unit corrections, batch 1 (MariaDB; the earlier files in this folder are SQLite-era history).
--
-- Fixes 9 inventory_item units that disagreed with how the item is actually sold, so that
-- StoreDiscountService's unit gate stops holding legitimate offers for review. A fresh database
-- built from seed/inventory_catalog.sql already has the new units.
--
-- Re-runnable: every statement only fires while the row still has its OLD unit, so applying it twice
-- (or against an already corrected database) changes nothing. recipe_ingredient carries its own unit
-- per ingredient, so recipes are not affected.
--
-- Snapshot first (see CLAUDE.md), then apply with:
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < migrations/0003_item_units_batch_1.sql

UPDATE inventory_item SET inventory_item_unit = 'g' WHERE inventory_item_id = 1680 AND inventory_item_unit = 'Stück';  -- Schokoriegel
UPDATE inventory_item SET inventory_item_unit = 'g' WHERE inventory_item_id = 1694 AND inventory_item_unit = 'Stück';  -- Kühlriegel
UPDATE inventory_item SET inventory_item_unit = 'ml' WHERE inventory_item_id = 366 AND inventory_item_unit = 'g';  -- buttermilch
UPDATE inventory_item SET inventory_item_unit = 'ml' WHERE inventory_item_id = 357 AND inventory_item_unit = 'g';  -- fettarme milch
UPDATE inventory_item SET inventory_item_unit = 'ml' WHERE inventory_item_id = 1071 AND inventory_item_unit = 'g';  -- ahornsirup
UPDATE inventory_item SET inventory_item_unit = 'Stück' WHERE inventory_item_id = 66 AND inventory_item_unit = 'g';  -- aubergine
UPDATE inventory_item SET inventory_item_unit = 'Stück' WHERE inventory_item_id = 187 AND inventory_item_unit = 'g';  -- limette
UPDATE inventory_item SET inventory_item_unit = 'Stück' WHERE inventory_item_id = 598 AND inventory_item_unit = 'g';  -- donut
UPDATE inventory_item SET inventory_item_unit = 'Stück' WHERE inventory_item_id = 597 AND inventory_item_unit = 'g';  -- berliner
