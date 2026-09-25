package de.dahaem.finance;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.shared.Database;
import de.dahaem.stores.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises PurchaseService.recordNewProductPurchase — the receipts-as-ground-truth path for
 * a purchase of a product that isn't in the catalog yet — against a real, temporary SQLite file
 * built from the subset of schema.sql it needs. No mocks: only Database's target file is swapped.
 */
class PurchaseServiceNewProductTest {

    private Path dbFile;
    private PurchaseRepository purchaseRepository;
    private PurchaseService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        dbFile = Files.createTempFile("dahaem-finance-new-product-test", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty("dahaem.db.path", dbFile.toAbsolutePath().toString());
        createSchema();
        purchaseRepository = new PurchaseRepository();
        service = new PurchaseService(purchaseRepository, new StoreRepository(), new InventoryRepository());
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("dahaem.db.path");
        Files.deleteIfExists(dbFile);
    }

    @Test
    void recordingABrandNewProductCreatesStoreBrandInventoryItemStoreItemAndPurchase() throws SQLException {
        int purchaseId = service.recordNewProductPurchase("Rewe", "Ja!", null,
                "oat drink", "hafergetränk", "dairy alternative",
                "ml", 1000, 1.19,
                2, 2.38, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 12, 1));

        assertEquals(1, purchaseId, "the purchase must have been inserted");

        assertEquals(1, storeId("Rewe"));
        assertEquals(1, brandId("Ja!"));

        int inventoryItemId = soleInventoryItemId();
        assertEquals("oat drink", inventoryItemNameEnglish(inventoryItemId));
        assertEquals(2, inventoryQuantity(inventoryItemId), "the purchased quantity must be applied");
        assertEquals("2026-12-01", inventoryEarliestExpDate(inventoryItemId));

        int storeItemId = soleStoreItemId();
        assertEquals(inventoryItemId, storeItemInventoryItemId(storeItemId));

        Purchase purchase = purchaseRepository.findAll().get(0);
        assertEquals(storeItemId, purchase.getStoreItemId());
        assertEquals(2, purchase.getQuantityPurchased());
        assertEquals(2.38, purchase.getPricePaid(), 0.001);
    }

    @Test
    void recordingAgainstAnExistingInventoryItemReusesItInsteadOfCreatingAGenericOne() throws SQLException {
        int existingItemId = seedInventoryItem("carrots", "karotten", "vegetable");

        service.recordNewProductPurchase("Aldi", "Gut Bio", existingItemId,
                null, null, null,
                "g", 500, 0.99,
                1, 0.99, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 10));

        assertEquals(1, countInventoryItems(), "no new generic inventory_item should have been created");
        assertEquals(1, inventoryQuantity(existingItemId));
    }

    @Test
    void reRecordingTheSameNewProductDoesNotDuplicateTheStoreItem() throws SQLException {
        service.recordNewProductPurchase("Rewe", "Ja!", null,
                "oat drink", "hafergetränk", "dairy alternative",
                "ml", 1000, 1.19,
                1, 1.19, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 12, 1));

        service.recordNewProductPurchase("Rewe", "Ja!", null,
                "oat drink", "hafergetränk", "dairy alternative",
                "ml", 1000, 1.19,
                1, 1.19, LocalDate.of(2026, 9, 26), LocalDate.of(2026, 12, 5));

        assertEquals(1, countStores());
        assertEquals(1, countBrands());
        assertEquals(1, countInventoryItems(), "the second purchase must reuse the generic item created by the first");
        assertEquals(1, countStoreItems(), "the second purchase must reuse the store_item created by the first");
        assertEquals(2, purchaseRepository.findAll().size());

        int inventoryItemId = soleInventoryItemId();
        assertEquals(2, inventoryQuantity(inventoryItemId), "both purchases must add to the same inventory_item");
    }

    // ---------------------------------------------------------------- helpers

    private int storeId(String name) throws SQLException {
        return scalarInt("SELECT store_id FROM store WHERE store_name = ?", name);
    }

    private int brandId(String name) throws SQLException {
        return scalarInt("SELECT brand_id FROM brand WHERE brand_name = ?", name);
    }

    private int scalarInt(String sql, String param) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "expected a row for " + param);
                return rs.getInt(1);
            }
        }
    }

    private int soleInventoryItemId() throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT inventory_item_id FROM inventory_item")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int soleStoreItemId() throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT store_item_id FROM store_item")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int storeItemInventoryItemId(int storeItemId) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT store_item_item_id FROM store_item WHERE store_item_id = ?")) {
            ps.setInt(1, storeItemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String inventoryItemNameEnglish(int id) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT inventory_item_name_english FROM inventory_item WHERE inventory_item_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private int inventoryQuantity(int inventoryItemId) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT inventory_item_quantity FROM inventory_item WHERE inventory_item_id = ?")) {
            ps.setInt(1, inventoryItemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String inventoryEarliestExpDate(int inventoryItemId) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT inventory_item_earliest_exp_date FROM inventory_item WHERE inventory_item_id = ?")) {
            ps.setInt(1, inventoryItemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private int countInventoryItems() throws SQLException {
        return countAll("inventory_item");
    }

    private int countStores() throws SQLException {
        return countAll("store");
    }

    private int countBrands() throws SQLException {
        return countAll("brand");
    }

    private int countStoreItems() throws SQLException {
        return countAll("store_item");
    }

    private int countAll(String table) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int seedInventoryItem(String nameEnglish, String nameDeutsch, String category) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO inventory_item (inventory_item_name_english, inventory_item_name_deutsch, " +
                             "inventory_item_quantity, inventory_item_unit, inventory_item_category) " +
                             "VALUES (?, ?, 0, 'g', ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nameEnglish);
            ps.setString(2, nameDeutsch);
            ps.setString(3, category);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void createSchema() throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute("CREATE TABLE inventory_item (" +
                    "inventory_item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "inventory_item_name_english TEXT NOT NULL, " +
                    "inventory_item_name_deutsch TEXT, " +
                    "inventory_item_quantity INTEGER NOT NULL DEFAULT 0, " +
                    "inventory_item_unit TEXT NOT NULL, " +
                    "inventory_item_category TEXT NOT NULL, " +
                    "inventory_item_subcategory TEXT, " +
                    "inventory_item_earliest_exp_date DATE)");

            st.execute("CREATE TABLE store (" +
                    "store_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "store_name TEXT NOT NULL UNIQUE)");

            st.execute("CREATE TABLE brand (" +
                    "brand_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "brand_name TEXT NOT NULL UNIQUE)");

            st.execute("CREATE TABLE store_item (" +
                    "store_item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "store_item_brand_id INTEGER NOT NULL REFERENCES brand(brand_id), " +
                    "store_item_store_id INTEGER NOT NULL REFERENCES store(store_id), " +
                    "store_item_item_id INTEGER NOT NULL REFERENCES inventory_item(inventory_item_id), " +
                    "store_item_package_unit TEXT NOT NULL, " +
                    "store_item_package_size INTEGER NOT NULL, " +
                    "store_item_price REAL, " +
                    "store_item_is_available BOOLEAN NOT NULL, " +
                    "store_item_deposit REAL, " +
                    "store_item_store_product_id INTEGER, " +
                    "store_item_external_id TEXT)");

            st.execute("CREATE TABLE purchase_history (" +
                    "purchase_history_purchase_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "purchase_history_store_item_id INTEGER NOT NULL, " +
                    "purchase_history_discount_id INTEGER, " +
                    "purchase_history_was_discounted BOOLEAN NOT NULL, " +
                    "purchase_history_purchase_date DATE NOT NULL, " +
                    "purchase_history_quantity_purchased INTEGER NOT NULL, " +
                    "purchase_history_price_paid REAL NOT NULL, " +
                    "purchase_history_expiration_date DATE NOT NULL)");
        }
    }
}
