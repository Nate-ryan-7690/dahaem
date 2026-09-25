package de.dahaem.stores;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All SQL for pulled store data: store_product (+ variants, price history),
 * store_offer, store_catalog_change and store_import_run.
 *
 * These are the RAW layer: what a chain sells, not yet matched to your catalog.
 * The existing store_item / store_discount tables stay the MATCHED layer
 * (they require a brand and an inventory item); filling them from here is a later step.
 */
public class StoreRepository {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS store_import_run (
                store_import_run_id          INTEGER PRIMARY KEY AUTOINCREMENT,
                store_import_run_store_id    INTEGER NOT NULL REFERENCES store(store_id),
                store_import_run_kind        TEXT NOT NULL,      -- RANGE / OFFERS
                store_import_run_started_at  TEXT NOT NULL,
                store_import_run_finished_at TEXT,
                store_import_run_status      TEXT NOT NULL,      -- RUNNING / OK / PARTIAL / FAILED
                store_import_run_pages       INTEGER,
                store_import_run_items       INTEGER,
                store_import_run_notes       TEXT)
            """,
            """
            CREATE TABLE IF NOT EXISTS store_product (
                store_product_id                INTEGER PRIMARY KEY AUTOINCREMENT,
                store_product_store_id          INTEGER NOT NULL REFERENCES store(store_id),
                store_product_external_id       TEXT NOT NULL,   -- the chain's own product id
                store_product_brand             TEXT,
                store_product_name              TEXT NOT NULL,
                store_product_sales_unit        TEXT,            -- e.g. "250-g-Packung"
                store_product_regular_price     REAL,
                store_product_base_price        REAL,            -- price per base unit
                store_product_base_unit         TEXT,            -- e.g. "kg", "Liter"
                store_product_deposit           REAL,            -- Pfand per unit (null = none)
                store_product_category          TEXT,
                store_product_description       TEXT,
                store_product_url               TEXT,
                store_product_is_available      BOOLEAN NOT NULL,
                store_product_first_seen        DATE NOT NULL,
                store_product_last_seen         DATE NOT NULL,
                store_product_inventory_item_id INTEGER REFERENCES inventory_item(inventory_item_id),  -- set by matching later
                UNIQUE (store_product_store_id, store_product_external_id))
            """,
            """
            CREATE TABLE IF NOT EXISTS store_product_variant (
                store_product_variant_id         INTEGER PRIMARY KEY AUTOINCREMENT,
                store_product_variant_product_id INTEGER NOT NULL REFERENCES store_product(store_product_id),
                store_product_variant_name       TEXT NOT NULL,  -- flavour, e.g. "Natur"
                store_product_variant_size       TEXT)           -- e.g. "150 g"
            """,
            """
            CREATE TABLE IF NOT EXISTS store_price_history (
                store_price_history_id            INTEGER PRIMARY KEY AUTOINCREMENT,
                store_price_history_product_id    INTEGER NOT NULL REFERENCES store_product(store_product_id),
                store_price_history_price         REAL NOT NULL,
                store_price_history_base_price    REAL,
                store_price_history_recorded_date DATE NOT NULL)
            """,
            """
            CREATE TABLE IF NOT EXISTS store_catalog_change (
                store_catalog_change_id            INTEGER PRIMARY KEY AUTOINCREMENT,
                store_catalog_change_run_id        INTEGER NOT NULL REFERENCES store_import_run(store_import_run_id),
                store_catalog_change_product_id    INTEGER NOT NULL REFERENCES store_product(store_product_id),
                store_catalog_change_type          TEXT NOT NULL,  -- NEW / REMOVED / RETURNED / PRICE
                store_catalog_change_old_price     REAL,
                store_catalog_change_new_price     REAL,
                store_catalog_change_detected_date DATE NOT NULL)
            """,
            """
            CREATE TABLE IF NOT EXISTS store_offer (
                store_offer_id           INTEGER PRIMARY KEY AUTOINCREMENT,
                store_offer_store_id     INTEGER NOT NULL REFERENCES store(store_id),
                store_offer_product_id   INTEGER REFERENCES store_product(store_product_id),  -- null if not in the range
                store_offer_external_id  TEXT NOT NULL,
                store_offer_brand        TEXT,
                store_offer_name         TEXT NOT NULL,
                store_offer_sales_unit   TEXT,
                store_offer_sale_price   REAL NOT NULL,        -- per unit
                store_offer_deal_quantity INTEGER NOT NULL DEFAULT 1,  -- units required for a multi-buy deal
                store_offer_deal_price   REAL,                 -- total for the deal quantity (null = normal single offer)
                store_offer_normal_price REAL,
                store_offer_base_price   REAL,
                store_offer_base_unit    TEXT,
                store_offer_deposit      REAL,                 -- Pfand per unit
                store_offer_promo_text   TEXT,
                store_offer_category     TEXT,
                store_offer_region       TEXT NOT NULL DEFAULT '',
                store_offer_valid_from   DATE NOT NULL,
                store_offer_valid_until  DATE NOT NULL,
                store_offer_pulled_on    DATE NOT NULL,
                store_offer_source       TEXT NOT NULL DEFAULT 'STORE',  -- STORE (direct) / AGGREGATOR / RECEIPT
                UNIQUE (store_offer_store_id, store_offer_external_id, store_offer_valid_from, store_offer_region))
            """
    };

    /**
     * store_item / store_discount already exist (matched layer). These columns hold the
     * deposit and multi-buy deal terms once a store_product/store_offer is matched to them.
     * Deposit lives on the item (constant per pack); deal terms live on the discount (per offer).
     */
    private static final Map<String, String> STORE_ITEM_COLUMNS = new LinkedHashMap<>() {{
        put("store_item_deposit", "REAL");
        // Ties a promoted store_item to the exact store_product it came from, so distinct
        // products (same brand+size, different price/variant) never collapse into one.
        put("store_item_store_product_id", "INTEGER REFERENCES store_product(store_product_id)");
        // Stable per-chain id for a flyer-bootstrapped store_item (the offer's external id),
        // since those have no store_product. One of the two is set; both null = manual entry.
        put("store_item_external_id", "TEXT");
    }};
    private static final Map<String, String> STORE_DISCOUNT_COLUMNS = new LinkedHashMap<>() {{
        put("store_discount_deal_quantity", "INTEGER NOT NULL DEFAULT 1");
        put("store_discount_deal_price", "REAL");
    }};
    /** Provenance on offers pulled before this column existed defaults to the direct-store source. */
    private static final Map<String, String> STORE_OFFER_COLUMNS = Map.of(
            "store_offer_source", "TEXT NOT NULL DEFAULT 'STORE'");

    /** Creates the store tables if they don't exist yet, and back-fills new columns. Safe to call every run. */
    public void ensureSchema() {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            for (String ddl : SCHEMA) {
                st.execute(ddl);
            }
            addMissingColumns(con, "store_item", STORE_ITEM_COLUMNS);
            addMissingColumns(con, "store_discount", STORE_DISCOUNT_COLUMNS);
            addMissingColumns(con, "store_offer", STORE_OFFER_COLUMNS);
            makeStoreItemPriceNullable(con);
        } catch (SQLException e) {
            throw new RuntimeException("Could not create store tables", e);
        }
    }

    /**
     * A flyer-bootstrapped store_item has no known normal price (the flyer price is the sale price,
     * which lives on store_discount), so store_item_price must allow NULL. SQLite can't drop a NOT NULL
     * constraint in place, so rebuild the table once. store_discount is the only referrer; foreign_keys
     * is toggled off around the swap. No-op once the column is already nullable.
     */
    private void makeStoreItemPriceNullable(Connection con) throws SQLException {
        boolean notNull;
        try (ResultSet rs = con.getMetaData().getColumns(null, null, "store_item", "store_item_price")) {
            if (!rs.next()) {
                return;                                    // table missing entirely; nothing to do
            }
            notNull = rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNoNulls;
        }
        if (!notNull) {
            return;
        }
        boolean prevAutoCommit = con.getAutoCommit();
        try (Statement st = con.createStatement()) {
            con.setAutoCommit(true);
            st.execute("PRAGMA foreign_keys=OFF");        // must be outside a transaction
            con.setAutoCommit(false);
            st.execute("CREATE TABLE store_item_new ("
                    + "store_item_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "store_item_brand_id INTEGER NOT NULL REFERENCES brand(brand_id), "
                    + "store_item_store_id INTEGER NOT NULL REFERENCES store(store_id), "
                    + "store_item_item_id INTEGER NOT NULL REFERENCES inventory_item(inventory_item_id), "
                    + "store_item_package_unit TEXT NOT NULL, "
                    + "store_item_package_size INTEGER NOT NULL, "
                    + "store_item_price REAL, "                                       // now nullable
                    + "store_item_is_available BOOLEAN NOT NULL, "
                    + "store_item_deposit REAL, "
                    + "store_item_store_product_id INTEGER REFERENCES store_product(store_product_id), "
                    + "store_item_external_id TEXT)");
            st.execute("INSERT INTO store_item_new (store_item_id, store_item_brand_id, store_item_store_id, "
                    + "store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, "
                    + "store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) "
                    + "SELECT store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, "
                    + "store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, "
                    + "store_item_deposit, store_item_store_product_id, store_item_external_id FROM store_item");
            st.execute("DROP TABLE store_item");
            st.execute("ALTER TABLE store_item_new RENAME TO store_item");
            con.commit();
            con.setAutoCommit(true);
            st.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(prevAutoCommit);
        }
    }

    /** Adds any of the given columns that the table doesn't have yet (SQLite has no "ADD COLUMN IF NOT EXISTS"). */
    private void addMissingColumns(Connection con, String table, Map<String, String> columns) throws SQLException {
        Set<String> existing = new HashSet<>();
        try (ResultSet rs = con.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                existing.add(rs.getString("COLUMN_NAME"));
            }
        }
        try (Statement st = con.createStatement()) {
            for (Map.Entry<String, String> col : columns.entrySet()) {
                if (!existing.contains(col.getKey())) {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN " + col.getKey() + " " + col.getValue());
                }
            }
        }
    }

    /** Id of a row in the existing `store` table, creating it if the chain isn't there yet. */
    public int findOrCreateStore(String name) {
        // Atomic upsert instead of check-then-act: store_name is UNIQUE, so ON CONFLICT DO NOTHING
        // makes the insert a no-op when the store already exists, and the SELECT then returns its id.
        // This removes the race where two callers both pass the SELECT and both INSERT a duplicate.
        String upsert = "INSERT INTO store (store_name) VALUES (?) ON CONFLICT(store_name) DO NOTHING";
        String select = "SELECT store_id FROM store WHERE store_name = ?";
        try (Connection con = Database.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(upsert)) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(select)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not find or create store " + name, e);
        }
    }

    // ---------------------------------------------------------------- import runs

    public int startRun(int storeId, String kind) {
        String sql = "INSERT INTO store_import_run (store_import_run_store_id, store_import_run_kind, "
                + "store_import_run_started_at, store_import_run_status) VALUES (?, ?, ?, 'RUNNING')";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, storeId);
            ps.setString(2, kind);
            ps.setString(3, LocalDateTime.now(JsonUtil.GERMANY).toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not start import run", e);
        }
    }

    public void finishRun(int runId, String status, int pages, int items, String notes) {
        String sql = "UPDATE store_import_run SET store_import_run_finished_at = ?, store_import_run_status = ?, "
                + "store_import_run_pages = ?, store_import_run_items = ?, store_import_run_notes = ? "
                + "WHERE store_import_run_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now(JsonUtil.GERMANY).toString());
            ps.setString(2, status);
            ps.setInt(3, pages);
            ps.setInt(4, items);
            ps.setString(5, notes);
            ps.setInt(6, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not finish import run " + runId, e);
        }
    }

    // ---------------------------------------------------------------- full range

    /** Everything already stored for a chain, keyed by the chain's product id. */
    public Map<String, KnownProduct> loadKnownProducts(int storeId) {
        String sql = "SELECT store_product_id, store_product_external_id, store_product_name, "
                + "store_product_regular_price, store_product_is_available "
                + "FROM store_product WHERE store_product_store_id = ?";
        Map<String, KnownProduct> known = new HashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double price = rs.getDouble("store_product_regular_price");
                    Double regular = rs.wasNull() ? null : price;
                    KnownProduct k = new KnownProduct(
                            rs.getInt("store_product_id"),
                            rs.getString("store_product_external_id"),
                            rs.getString("store_product_name"),
                            regular,
                            rs.getBoolean("store_product_is_available"));
                    known.put(k.externalId(), k);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load store products for store " + storeId, e);
        }
        return known;
    }

    /**
     * Saves one range pull in a single transaction (all or nothing):
     * inserts new products, updates known ones, replaces their variants, records
     * price history for `recordPriceFor`, marks `removedIds` unavailable and stores the changes.
     */
    public void saveRange(int storeId, int runId, List<StoreProduct> products, Map<String, KnownProduct> known,
                          List<Integer> removedIds, List<CatalogChange> changes, Set<String> recordPriceFor,
                          LocalDate today) {
        String insertProduct = "INSERT INTO store_product (store_product_store_id, store_product_external_id, "
                + "store_product_brand, store_product_name, store_product_sales_unit, store_product_regular_price, "
                + "store_product_base_price, store_product_base_unit, store_product_deposit, store_product_category, "
                + "store_product_description, store_product_url, store_product_is_available, "
                + "store_product_first_seen, store_product_last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateProduct = "UPDATE store_product SET store_product_brand = ?, store_product_name = ?, "
                + "store_product_sales_unit = ?, store_product_regular_price = ?, store_product_base_price = ?, "
                + "store_product_base_unit = ?, store_product_deposit = ?, store_product_category = ?, "
                + "store_product_description = ?, store_product_url = ?, store_product_is_available = ?, "
                + "store_product_last_seen = ? WHERE store_product_id = ?";
        String deleteVariants = "DELETE FROM store_product_variant WHERE store_product_variant_product_id = ?";
        String insertVariant = "INSERT INTO store_product_variant (store_product_variant_product_id, "
                + "store_product_variant_name, store_product_variant_size) VALUES (?, ?, ?)";
        String insertPrice = "INSERT INTO store_price_history (store_price_history_product_id, "
                + "store_price_history_price, store_price_history_base_price, store_price_history_recorded_date) "
                + "VALUES (?, ?, ?, ?)";
        String markRemoved = "UPDATE store_product SET store_product_is_available = 0 WHERE store_product_id = ?";
        String insertChange = "INSERT INTO store_catalog_change (store_catalog_change_run_id, "
                + "store_catalog_change_product_id, store_catalog_change_type, store_catalog_change_old_price, "
                + "store_catalog_change_new_price, store_catalog_change_detected_date) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement insP = con.prepareStatement(insertProduct, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement updP = con.prepareStatement(updateProduct);
                 PreparedStatement delV = con.prepareStatement(deleteVariants);
                 PreparedStatement insV = con.prepareStatement(insertVariant);
                 PreparedStatement insH = con.prepareStatement(insertPrice);
                 PreparedStatement rem = con.prepareStatement(markRemoved);
                 PreparedStatement insC = con.prepareStatement(insertChange)) {

                Map<String, Integer> idByExternal = new HashMap<>();
                known.forEach((externalId, k) -> idByExternal.put(externalId, k.id()));
                String day = today.toString();

                for (StoreProduct p : products) {
                    Integer id = idByExternal.get(p.externalId());
                    if (id == null) {
                        insP.setInt(1, storeId);
                        insP.setString(2, p.externalId());
                        insP.setString(3, p.brand());
                        insP.setString(4, p.name());
                        insP.setString(5, p.salesUnit());
                        insP.setObject(6, p.regularPrice());
                        insP.setObject(7, p.basePrice());
                        insP.setString(8, p.baseUnit());
                        insP.setObject(9, p.deposit());
                        insP.setString(10, p.category());
                        insP.setString(11, p.description());
                        insP.setString(12, p.url());
                        insP.setBoolean(13, p.available());
                        insP.setString(14, day);
                        insP.setString(15, day);
                        insP.executeUpdate();
                        try (ResultSet keys = insP.getGeneratedKeys()) {
                            keys.next();
                            id = keys.getInt(1);
                        }
                        idByExternal.put(p.externalId(), id);
                    } else {
                        updP.setString(1, p.brand());
                        updP.setString(2, p.name());
                        updP.setString(3, p.salesUnit());
                        updP.setObject(4, p.regularPrice());
                        updP.setObject(5, p.basePrice());
                        updP.setString(6, p.baseUnit());
                        updP.setObject(7, p.deposit());
                        updP.setString(8, p.category());
                        updP.setString(9, p.description());
                        updP.setString(10, p.url());
                        updP.setBoolean(11, p.available());
                        updP.setString(12, day);
                        updP.setInt(13, id);
                        updP.executeUpdate();
                    }

                    delV.setInt(1, id);
                    delV.executeUpdate();
                    for (ProductVariant v : p.variants()) {
                        insV.setInt(1, id);
                        insV.setString(2, v.name());
                        insV.setString(3, v.size());
                        insV.addBatch();
                    }
                    if (recordPriceFor.contains(p.externalId()) && p.regularPrice() != null) {
                        insH.setInt(1, id);
                        insH.setDouble(2, p.regularPrice());
                        insH.setObject(3, p.basePrice());
                        insH.setString(4, day);
                        insH.addBatch();
                    }
                }
                insV.executeBatch();
                insH.executeBatch();

                for (int id : removedIds) {
                    rem.setInt(1, id);
                    rem.addBatch();
                }
                rem.executeBatch();

                for (CatalogChange c : changes) {
                    Integer id = idByExternal.get(c.externalId());
                    if (id == null) {
                        continue;
                    }
                    insC.setInt(1, runId);
                    insC.setInt(2, id);
                    insC.setString(3, c.type().name());
                    insC.setObject(4, c.oldPrice());
                    insC.setObject(5, c.newPrice());
                    insC.setString(6, day);
                    insC.addBatch();
                }
                insC.executeBatch();

                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not save store range for store " + storeId, e);
        }
    }

    // ---------------------------------------------------------------- matching to the catalog

    /** All inventory_item rows with their names pre-normalized for matching. */
    public List<CatalogItem> loadCatalog() {
        String sql = "SELECT inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch FROM inventory_item";
        List<CatalogItem> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String en = rs.getString(2);
                String de = rs.getString(3);
                out.add(new CatalogItem(rs.getInt(1), en, de,
                        Normalizer.key(en == null ? "" : en), Normalizer.key(de == null ? "" : de)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load catalog", e);
        }
        return out;
    }

    /** Store products of one store that aren't linked to a catalog item yet. */
    public List<StoreProductRef> loadStoreProductsForMatch(int storeId) {
        String sql = "SELECT store_product_id, store_product_name, store_product_category FROM store_product "
                + "WHERE store_product_store_id = ? AND store_product_inventory_item_id IS NULL";
        List<StoreProductRef> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StoreProductRef(rs.getInt(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load store products for matching", e);
        }
        return out;
    }

    /** Sets store_product_inventory_item_id for the matched products, in one transaction. */
    /** Matched, available store_products with a price: the candidates for promotion to store_item. */
    public List<PromotableProduct> loadPromotableProducts() {
        String sql = "SELECT store_product_id, store_product_store_id, store_product_brand, store_product_name, "
                + "store_product_sales_unit, store_product_regular_price, store_product_deposit, "
                + "store_product_is_available, store_product_inventory_item_id FROM store_product "
                + "WHERE store_product_inventory_item_id IS NOT NULL AND store_product_is_available = 1 "
                + "AND store_product_regular_price IS NOT NULL";
        List<PromotableProduct> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new PromotableProduct(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), (Double) rs.getObject(6), (Double) rs.getObject(7),
                        rs.getBoolean(8), rs.getInt(9)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load promotable products", e);
        }
        return out;
    }

    /** brand_id for a brand name, inserting the brand if it's new (brand_name is UNIQUE). */
    public int findOrCreateBrand(Connection con, String name) throws SQLException {
        try (PreparedStatement sel = con.prepareStatement("SELECT brand_id FROM brand WHERE brand_name = ?")) {
            sel.setString(1, name);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement ins = con.prepareStatement(
                "INSERT INTO brand (brand_name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ins.setString(1, name);
            ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Removes promotion rows made before per-product keying existed (store_product_id IS NULL). One-time self-heal. */
    public int deleteLegacyStoreItems(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            return st.executeUpdate("DELETE FROM store_item WHERE store_item_store_product_id IS NULL");
        }
    }

    /**
     * Insert or refresh one verified store_item, keyed by its source store_product, so each
     * distinct product is its own store_item and nothing collapses. Re-running promotion updates
     * price/availability/deposit (and the parsed size/brand) in place. Returns true if newly inserted.
     */
    public boolean upsertStoreItem(Connection con, int storeProductId, int storeId, int brandId, int itemId,
                                   String unit, int size, double price, boolean available, Double deposit)
            throws SQLException {
        Integer existing = null;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT store_item_id FROM store_item WHERE store_item_store_product_id = ?")) {
            ps.setInt(1, storeProductId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existing = rs.getInt(1);
                }
            }
        }
        if (existing != null) {
            try (PreparedStatement up = con.prepareStatement("UPDATE store_item SET store_item_brand_id = ?, "
                    + "store_item_item_id = ?, store_item_package_unit = ?, store_item_package_size = ?, "
                    + "store_item_price = ?, store_item_is_available = ?, store_item_deposit = ? "
                    + "WHERE store_item_id = ?")) {
                up.setInt(1, brandId);
                up.setInt(2, itemId);
                up.setString(3, unit);
                up.setInt(4, size);
                up.setDouble(5, price);
                up.setBoolean(6, available);
                up.setObject(7, deposit);
                up.setInt(8, existing);
                up.executeUpdate();
            }
            return false;
        }
        try (PreparedStatement ins = con.prepareStatement("INSERT INTO store_item (store_item_brand_id, "
                + "store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, "
                + "store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ins.setInt(1, brandId);
            ins.setInt(2, storeId);
            ins.setInt(3, itemId);
            ins.setString(4, unit);
            ins.setInt(5, size);
            ins.setDouble(6, price);
            ins.setBoolean(7, available);
            ins.setObject(8, deposit);
            ins.setInt(9, storeProductId);
            ins.executeUpdate();
        }
        return true;
    }

    /** All offers, newest validity first, for discount promotion. Optionally one store only. */
    public List<OfferRow> loadOffersForPromotion(Integer storeId) {
        String sql = "SELECT store_offer_store_id, store_offer_external_id, store_offer_brand, store_offer_name, "
                + "store_offer_sales_unit, store_offer_sale_price, store_offer_deal_quantity, store_offer_deal_price, "
                + "store_offer_normal_price, store_offer_deposit, store_offer_promo_text, store_offer_valid_from, "
                + "store_offer_valid_until, store_offer_source FROM store_offer"
                + (storeId != null ? " WHERE store_offer_store_id = ?" : "");
        List<OfferRow> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (storeId != null) {
                ps.setInt(1, storeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OfferRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getDouble(6), rs.getInt(7), (Double) rs.getObject(8),
                            (Double) rs.getObject(9), (Double) rs.getObject(10), rs.getString(11),
                            rs.getString(12), rs.getString(13), rs.getString(14)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load offers for promotion", e);
        }
        return out;
    }

    /**
     * Find-or-create a flyer-bootstrapped store_item, keyed by (store, external_id). Updates the
     * shell (brand/item/size/deposit) and the normal price only when we actually know one (a non-null
     * normalPrice never gets overwritten with null). Returns the store_item id.
     */
    public int upsertStoreItemFromOffer(Connection con, int storeId, String externalId, int brandId, int itemId,
                                        String unit, int size, Double normalPrice, boolean available, Double deposit)
            throws SQLException {
        Integer existing = null;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT store_item_id FROM store_item WHERE store_item_store_id = ? AND store_item_external_id = ?")) {
            ps.setInt(1, storeId);
            ps.setString(2, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existing = rs.getInt(1);
                }
            }
        }
        if (existing != null) {
            // keep an existing normal price if this offer has none (COALESCE(new, old))
            try (PreparedStatement up = con.prepareStatement("UPDATE store_item SET store_item_brand_id = ?, "
                    + "store_item_item_id = ?, store_item_package_unit = ?, store_item_package_size = ?, "
                    + "store_item_price = COALESCE(?, store_item_price), store_item_is_available = ?, "
                    + "store_item_deposit = ? WHERE store_item_id = ?")) {
                up.setInt(1, brandId);
                up.setInt(2, itemId);
                up.setString(3, unit);
                up.setInt(4, size);
                up.setObject(5, normalPrice);
                up.setBoolean(6, available);
                up.setObject(7, deposit);
                up.setInt(8, existing);
                up.executeUpdate();
            }
            return existing;
        }
        // No offer row yet: reuse an existing store_item for the same product (same store, generic item,
        // brand, pack) if one exists — typically an ALDI range item, since ALDI's flyer and range are the
        // same source. This hangs the discount on the real product instead of creating a parallel offer row.
        // A range-sourced row (store_product_id set) wins over a prior offer-sourced one; the reused row's
        // normal price is left untouched (the range price is the real one).
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT store_item_id FROM store_item WHERE store_item_store_id = ? AND store_item_item_id = ? "
                + "AND store_item_brand_id = ? AND store_item_package_unit = ? AND store_item_package_size = ? "
                + "ORDER BY (store_item_store_product_id IS NOT NULL) DESC, store_item_id ASC LIMIT 1")) {
            ps.setInt(1, storeId);
            ps.setInt(2, itemId);
            ps.setInt(3, brandId);
            ps.setString(4, unit);
            ps.setInt(5, size);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement ins = con.prepareStatement("INSERT INTO store_item (store_item_brand_id, "
                + "store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, "
                + "store_item_price, store_item_is_available, store_item_deposit, store_item_external_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ins.setInt(1, brandId);
            ins.setInt(2, storeId);
            ins.setInt(3, itemId);
            ins.setString(4, unit);
            ins.setInt(5, size);
            ins.setObject(6, normalPrice);
            ins.setBoolean(7, available);
            ins.setObject(8, deposit);
            ins.setString(9, externalId);
            ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Insert or refresh a store_discount on a store_item, keyed by (store_item, start_date). Returns true if new. */
    public boolean upsertDiscount(Connection con, int storeItemId, String startDate, String endDate,
                                  double price, int dealQuantity, Double dealPrice, String notes) throws SQLException {
        Integer existing = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT store_discount_id FROM store_discount "
                + "WHERE store_discount_store_item_id = ? AND store_discount_start_date = ?")) {
            ps.setInt(1, storeItemId);
            ps.setString(2, startDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existing = rs.getInt(1);
                }
            }
        }
        if (existing != null) {
            try (PreparedStatement up = con.prepareStatement("UPDATE store_discount SET store_discount_end_date = ?, "
                    + "store_discount_price = ?, store_discount_deal_quantity = ?, store_discount_deal_price = ?, "
                    + "store_discount_notes = ? WHERE store_discount_id = ?")) {
                up.setString(1, endDate);
                up.setDouble(2, price);
                up.setInt(3, dealQuantity);
                up.setObject(4, dealPrice);
                up.setString(5, notes);
                up.setInt(6, existing);
                up.executeUpdate();
            }
            return false;
        }
        try (PreparedStatement ins = con.prepareStatement("INSERT INTO store_discount (store_discount_store_item_id, "
                + "store_discount_start_date, store_discount_end_date, store_discount_price, store_discount_notes, "
                + "store_discount_deal_quantity, store_discount_deal_price) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ins.setInt(1, storeItemId);
            ins.setString(2, startDate);
            ins.setString(3, endDate);
            ins.setDouble(4, price);
            ins.setString(5, notes);
            ins.setInt(6, dealQuantity);
            ins.setObject(7, dealPrice);
            ins.executeUpdate();
        }
        return true;
    }

    /** Opens one connection/transaction for a batch of store_item upserts (used by StorePromotionService). */
    public Connection openTransaction() {
        try {
            Connection con = Database.getConnection();
            con.setAutoCommit(false);
            return con;
        } catch (SQLException e) {
            throw new RuntimeException("Could not open transaction", e);
        }
    }

    public void saveMatches(Map<Integer, Integer> matches) {
        String sql = "UPDATE store_product SET store_product_inventory_item_id = ? WHERE store_product_id = ?";
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (Map.Entry<Integer, Integer> m : matches.entrySet()) {
                    ps.setInt(1, m.getValue());
                    ps.setInt(2, m.getKey());
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not save product matches", e);
        }
    }

    // ---------------------------------------------------------------- offers

    /**
     * Inserts offers, or refreshes them if the same offer (store, product, start date, region)
     * was pulled before, e.g. first from next week's preview, then from the live page.
     * Links each offer to its store_product when the chain's range is known.
     */
    public int saveOffers(int storeId, List<StoreOffer> offers, LocalDate pulledOn, String source) {
        String sql = "INSERT INTO store_offer (store_offer_store_id, store_offer_product_id, store_offer_external_id, "
                + "store_offer_brand, store_offer_name, store_offer_sales_unit, store_offer_sale_price, "
                + "store_offer_deal_quantity, store_offer_deal_price, store_offer_normal_price, store_offer_base_price, "
                + "store_offer_base_unit, store_offer_deposit, store_offer_promo_text, "
                + "store_offer_category, store_offer_region, store_offer_valid_from, store_offer_valid_until, "
                + "store_offer_pulled_on, store_offer_source) "
                + "VALUES (?, (SELECT store_product_id FROM store_product "
                + "            WHERE store_product_store_id = ? AND store_product_external_id = ?), "
                + "        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (store_offer_store_id, store_offer_external_id, store_offer_valid_from, store_offer_region) "
                + "DO UPDATE SET store_offer_product_id = excluded.store_offer_product_id, "
                + "store_offer_brand = excluded.store_offer_brand, store_offer_name = excluded.store_offer_name, "
                + "store_offer_sales_unit = excluded.store_offer_sales_unit, "
                + "store_offer_sale_price = excluded.store_offer_sale_price, "
                + "store_offer_deal_quantity = excluded.store_offer_deal_quantity, "
                + "store_offer_deal_price = excluded.store_offer_deal_price, "
                + "store_offer_normal_price = excluded.store_offer_normal_price, "
                + "store_offer_base_price = excluded.store_offer_base_price, "
                + "store_offer_base_unit = excluded.store_offer_base_unit, "
                + "store_offer_deposit = excluded.store_offer_deposit, "
                + "store_offer_promo_text = excluded.store_offer_promo_text, "
                + "store_offer_category = excluded.store_offer_category, "
                + "store_offer_valid_until = excluded.store_offer_valid_until, "
                + "store_offer_pulled_on = excluded.store_offer_pulled_on, "
                + "store_offer_source = excluded.store_offer_source";
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (StoreOffer o : offers) {
                    ps.setInt(1, storeId);
                    ps.setInt(2, storeId);
                    ps.setString(3, o.externalId());
                    ps.setString(4, o.externalId());
                    ps.setString(5, o.brand());
                    ps.setString(6, o.name());
                    ps.setString(7, o.salesUnit());
                    ps.setDouble(8, o.salePrice());
                    ps.setInt(9, o.dealQuantity());
                    ps.setObject(10, o.dealPrice());
                    ps.setObject(11, o.normalPrice());
                    ps.setObject(12, o.basePrice());
                    ps.setString(13, o.baseUnit());
                    ps.setObject(14, o.deposit());
                    ps.setString(15, o.promoText());
                    ps.setString(16, o.category());
                    ps.setString(17, o.region() == null ? "" : o.region());
                    ps.setString(18, o.validFrom().toString());
                    ps.setString(19, o.validUntil().toString());
                    ps.setString(20, pulledOn.toString());
                    ps.setString(21, source);
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not save offers for store " + storeId, e);
        }
        return offers.size();
    }
}
