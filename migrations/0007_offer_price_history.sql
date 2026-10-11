-- Offer price-change history: store_offer_price_change (MariaDB).
--
-- Why: StoreRepository.saveOffers upserts store_offer on (store, external id, valid_from, region)
-- with ON DUPLICATE KEY UPDATE. The refresh runs daily, so a re-pull in the same week overwrote the
-- sale / normal / deal price and a mid-week price change was lost. From now on saveOffers writes one
-- row here, in the same transaction, whenever a re-pulled offer's price differs from the stored one.
-- A new offer gets no row: the store_offer row itself is the first observation.
--
-- What it does — it ONLY ADDS: one new table. No existing table, column or row is touched.
--
-- Re-runnable: CREATE TABLE IF NOT EXISTS skips the table when it is already there.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply on llmbox from the PHP project folder:
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0007_offer_price_history.sql
-- Not applied to the live database by the PR that adds it.

CREATE TABLE IF NOT EXISTS store_offer_price_change (
    store_offer_price_change_id               INTEGER PRIMARY KEY AUTO_INCREMENT,
    store_offer_price_change_offer_id         INTEGER NOT NULL,
    store_offer_price_change_old_sale_price   DECIMAL(10,2) NOT NULL,
    store_offer_price_change_new_sale_price   DECIMAL(10,2) NOT NULL,
    store_offer_price_change_old_normal_price DECIMAL(10,2),
    store_offer_price_change_new_normal_price DECIMAL(10,2),
    store_offer_price_change_old_deal_price   DECIMAL(10,2),
    store_offer_price_change_new_deal_price   DECIMAL(10,2),
    store_offer_price_change_observed_on      DATE NOT NULL,      -- pulled_on of the pull that saw the new price
    store_offer_price_change_created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX store_offer_price_change_offer_observed (store_offer_price_change_offer_id, store_offer_price_change_observed_on),
    FOREIGN KEY(store_offer_price_change_offer_id) REFERENCES store_offer(store_offer_id));
