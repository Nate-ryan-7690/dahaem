-- Barcodes: which product a barcode number is, and which store items carry it (MariaDB).
--
-- Why: unpacking groceries by barcode number needs "this number is this product" to be remembered once and found
-- again, and each store's own store_item (price, offers) to be tied to it. Decided by Nate: numbers first (typed or
-- from a list), scanning in the app later; each store item is tracked specifically.
--
-- What it does:
--   1. product_barcode: the product a barcode number identifies, independent of any store: the catalogue item
--      (inventory_item), the brand (NULL = no brand), the pack (size + unit, like store_item) and the name as printed.
--      The number is stored as GTIN-13 (a 12-digit UPC-A gets a leading 0; 8 and 14 digits stay), at most 14 digits.
--   2. product_barcode_store_item: which store_items carry the barcode. The same number is on the shelf in several
--      stores and each store has its own store_item, so this is a link table, one row per (barcode, store item).
-- Nothing existing is changed or deleted. Re-runnable: CREATE TABLE IF NOT EXISTS skips what exists.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0020_product_barcode.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks everything: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

CREATE TABLE IF NOT EXISTS product_barcode (
    product_barcode_code         VARCHAR(14)  NOT NULL PRIMARY KEY,
    product_barcode_item_id      INTEGER      NOT NULL,
    product_barcode_brand_id     INTEGER      NULL,
    product_barcode_package_unit VARCHAR(20)  NOT NULL,
    product_barcode_package_size INTEGER      NOT NULL,
    product_barcode_name         TEXT         NULL,
    product_barcode_created_date DATE         NOT NULL,
    FOREIGN KEY (product_barcode_item_id) REFERENCES inventory_item(inventory_item_id),
    FOREIGN KEY (product_barcode_brand_id) REFERENCES brand(brand_id));

CREATE TABLE IF NOT EXISTS product_barcode_store_item (
    product_barcode_store_item_code          VARCHAR(14) NOT NULL,
    product_barcode_store_item_store_item_id INTEGER     NOT NULL,
    PRIMARY KEY (product_barcode_store_item_code, product_barcode_store_item_store_item_id),
    FOREIGN KEY (product_barcode_store_item_code) REFERENCES product_barcode(product_barcode_code),
    FOREIGN KEY (product_barcode_store_item_store_item_id) REFERENCES store_item(store_item_id));

-- The check: every line must say OK.
SELECT 'product_barcode exists with its two foreign keys' AS what,
       IF((SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'product_barcode' AND REFERENCED_TABLE_NAME IS NOT NULL) = 2, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'product_barcode_store_item exists with its two foreign keys',
       IF((SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'product_barcode_store_item' AND REFERENCED_TABLE_NAME IS NOT NULL) = 2, 'OK', 'CHECK');
