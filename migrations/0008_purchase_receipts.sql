-- Purchase receipts: purchase_receipt, purchase_receipt_line, purchase_receipt_adjustment (MariaDB).
--
-- Why: purchase_history does not fit a till receipt (store_item_id and expiration_date NOT NULL,
-- INTEGER quantity, no link to the receipt), and PurchaseService.recordPurchase adds to the
-- inventory stock, which a historical import must never do. Receipts get their own tables:
-- every printed product line is stored, matched to an inventory item or not; coupons, discounts,
-- Pfand and Leergut are adjustments. Written by de.dahaem.receipts.ReceiptImportRunner.
-- purchase_history, inventory_item and every other existing table stay untouched.
--
-- Money rules (the importer checks them per receipt before it writes anything):
--   paid_total = line_total + the discount adjustments attached to that line
--                (types APP_PRICE_DISCOUNT, APP_COUPON, LOYALTY_ITEM, FRESHNESS_DISCOUNT, OTHER);
--   SUM(paid_total) + every other adjustment (deposits, returns, unattached discounts) = total.
-- Amounts are signed as they affect the total: discounts and returns negative, deposits positive.
--
-- Duplicate protection: Penny receipt numbers are only 4 digits and repeat over time, so the key
-- is (store, purchase date-time, receipt number).
--
-- What it does — it ONLY ADDS: three new tables. No existing table, column or row is touched.
--
-- Re-runnable: CREATE TABLE IF NOT EXISTS skips a table that is already there.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply on llmbox from the PHP project folder:
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0008_purchase_receipts.sql
-- Not applied to the live database by the PR that adds it.

CREATE TABLE IF NOT EXISTS purchase_receipt (
    purchase_receipt_id             INTEGER PRIMARY KEY AUTO_INCREMENT,
    purchase_receipt_store_id       INTEGER NOT NULL,
    purchase_receipt_purchased_at   DATETIME NOT NULL,
    purchase_receipt_receipt_number VARCHAR(20) NOT NULL,     -- as printed ("Beleg-Nr."); repeats over time
    purchase_receipt_total          DECIMAL(10,2) NOT NULL,   -- printed "SUMME EUR"
    purchase_receipt_app_savings    DECIMAL(10,2),            -- "Mit der PENNY App ... gespart"
    purchase_receipt_other_savings  DECIMAL(10,2),            -- "Sonstige Vorteile"
    purchase_receipt_loyalty_points INTEGER,                  -- Treuepunkte earned
    purchase_receipt_imported_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY purchase_receipt_store_time_number
        (purchase_receipt_store_id, purchase_receipt_purchased_at, purchase_receipt_receipt_number),
    FOREIGN KEY(purchase_receipt_store_id) REFERENCES store(store_id));

CREATE TABLE IF NOT EXISTS purchase_receipt_line (
    purchase_receipt_line_id                INTEGER PRIMARY KEY AUTO_INCREMENT,
    purchase_receipt_line_receipt_id        INTEGER NOT NULL,
    purchase_receipt_line_line_no           INTEGER NOT NULL,        -- 1-based order of the products on the receipt
    purchase_receipt_line_raw_name          VARCHAR(255) NOT NULL,   -- as printed ("ESL MILCH 3,5%")
    purchase_receipt_line_brand             VARCHAR(100),            -- only from a known prefix ("LH" = Lindenhof); receipts rarely carry one
    purchase_receipt_line_quantity          DECIMAL(10,3) NOT NULL,  -- pieces, or kg for weighed goods
    purchase_receipt_line_unit              VARCHAR(10) NOT NULL,    -- Stk / kg
    purchase_receipt_line_weight_kg         DECIMAL(10,3),
    purchase_receipt_line_price_per_kg      DECIMAL(10,2),
    purchase_receipt_line_unit_price        DECIMAL(10,2) NOT NULL,
    purchase_receipt_line_line_total        DECIMAL(10,2) NOT NULL,  -- printed, before discounts
    purchase_receipt_line_paid_total        DECIMAL(10,2) NOT NULL,  -- after the discounts attached to this line
    purchase_receipt_line_tax_symbol        VARCHAR(2),
    purchase_receipt_line_tax_rate          DECIMAL(5,2),
    purchase_receipt_line_discount_excluded BOOLEAN NOT NULL DEFAULT FALSE,  -- the receipt's "*" marker
    purchase_receipt_line_deposit           DECIMAL(10,2),           -- Pfand per unit; booked as an adjustment
    purchase_receipt_line_inventory_item_id INTEGER,                 -- NULL = not matched (yet)
    purchase_receipt_line_match_source      VARCHAR(10),             -- how the item was found; NULL when unmatched
    UNIQUE KEY purchase_receipt_line_receipt_no (purchase_receipt_line_receipt_id, purchase_receipt_line_line_no),
    INDEX purchase_receipt_line_item (purchase_receipt_line_inventory_item_id),
    CONSTRAINT purchase_receipt_line_match_source_valid
        CHECK (purchase_receipt_line_match_source IN ('override', 'rule', 'manual')),
    CONSTRAINT purchase_receipt_line_source_needs_item
        CHECK (purchase_receipt_line_match_source IS NULL OR purchase_receipt_line_inventory_item_id IS NOT NULL),
    FOREIGN KEY(purchase_receipt_line_receipt_id) REFERENCES purchase_receipt(purchase_receipt_id),
    FOREIGN KEY(purchase_receipt_line_inventory_item_id) REFERENCES inventory_item(inventory_item_id));

CREATE TABLE IF NOT EXISTS purchase_receipt_adjustment (
    purchase_receipt_adjustment_id         INTEGER PRIMARY KEY AUTO_INCREMENT,
    purchase_receipt_adjustment_receipt_id INTEGER NOT NULL,
    purchase_receipt_adjustment_line_id    INTEGER,                  -- the product line it belongs to; NULL if none
    purchase_receipt_adjustment_type       VARCHAR(20) NOT NULL,
    purchase_receipt_adjustment_amount     DECIMAL(10,2) NOT NULL,   -- signed as it affects the total
    purchase_receipt_adjustment_raw_text   VARCHAR(255) NOT NULL,    -- as printed ("App-Coupon 10% Waren")
    CONSTRAINT purchase_receipt_adjustment_type_valid
        CHECK (purchase_receipt_adjustment_type IN ('APP_PRICE_DISCOUNT', 'APP_COUPON', 'LOYALTY_ITEM',
            'FRESHNESS_DISCOUNT', 'DEPOSIT', 'DEPOSIT_RETURN', 'OTHER')),
    FOREIGN KEY(purchase_receipt_adjustment_receipt_id) REFERENCES purchase_receipt(purchase_receipt_id),
    FOREIGN KEY(purchase_receipt_adjustment_line_id) REFERENCES purchase_receipt_line(purchase_receipt_line_id));
