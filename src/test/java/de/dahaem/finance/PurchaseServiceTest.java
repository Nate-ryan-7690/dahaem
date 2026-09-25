package de.dahaem.finance;

import de.dahaem.shared.Database;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the finance slice against a real, temporary SQLite file built from the subset
 * of schema.sql it needs (inventory_item/store/brand/store_item/purchase_history), with a
 * store_item seeded and linked to an inventory_item, the way store_item.store_item_item_id
 * links to inventory_item in the real schema. The repository under test is never mocked:
 * only Database's target file is swapped out.
 */
class PurchaseServiceTest {

    private Path dbFile;
    private PurchaseRepository repository;
    private PurchaseService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        dbFile = Files.createTempFile("dahaem-finance-test", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty("dahaem.db.path", dbFile.toAbsolutePath().toString());
        createSchema();
        repository = new PurchaseRepository();
        service = new PurchaseService(repository);
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("dahaem.db.path");
        Files.deleteIfExists(dbFile);
    }

    @Test
    void recordPurchaseInsertsPurchaseAndPullsEarliestExpiryDownInOneTransaction() throws SQLException {
        seedInventoryItem(1, 5, "2026-10-01");
        seedStoreItem(1, 1);

        int purchaseId = service.recordPurchase(1, null, false, LocalDate.of(2026, 9, 25),
                3, 4.50, LocalDate.of(2026, 9, 20));

        List<Purchase> purchases = repository.findAll();
        assertEquals(1, purchases.size());
        Purchase purchase = purchases.get(0);
        assertEquals(purchaseId, purchase.getId());
        assertEquals(1, purchase.getStoreItemId());
        assertNull(purchase.getDiscountId());
        assertTrue(!purchase.isWasDiscounted());
        assertEquals(LocalDate.of(2026, 9, 25), purchase.getPurchaseDate());
        assertEquals(3, purchase.getQuantityPurchased());
        assertEquals(4.50, purchase.getPricePaid(), 0.001);
        assertEquals(LocalDate.of(2026, 9, 20), purchase.getExpirationDate());

        assertEquals(8, inventoryQuantity(1), "quantity must increase by the amount purchased");
        assertEquals("2026-09-20", inventoryEarliestExpDate(1),
                "an earlier purchase expiry must pull the inventory expiry date down");
    }

    @Test
    void recordPurchaseKeepsTheEarlierExpiryWhenTheNewBatchExpiresLater() throws SQLException {
        seedInventoryItem(2, 10, "2026-09-20");
        seedStoreItem(2, 2);

        service.recordPurchase(2, null, false, LocalDate.of(2026, 9, 25),
                4, 6.00, LocalDate.of(2026, 11, 1));

        assertEquals(14, inventoryQuantity(2));
        assertEquals("2026-09-20", inventoryEarliestExpDate(2),
                "a later-expiring batch must not push the earliest expiry date back");
    }

    @Test
    void recordPurchaseSetsExpiryWhenNoneWasStoredYet() throws SQLException {
        seedInventoryItem(3, 0, null);
        seedStoreItem(3, 3);

        service.recordPurchase(3, null, false, LocalDate.of(2026, 9, 25),
                2, 3.00, LocalDate.of(2026, 12, 15));

        assertEquals(2, inventoryQuantity(3));
        assertEquals("2026-12-15", inventoryEarliestExpDate(3));
    }

    @Test
    void recordPurchaseRollsBackBothWritesWhenTheStoreItemDoesNotExist() throws SQLException {
        seedInventoryItem(4, 5, "2026-10-01");

        assertThrows(IllegalArgumentException.class, () ->
                service.recordPurchase(999, null, false, LocalDate.of(2026, 9, 25),
                        1, 1.00, LocalDate.of(2026, 10, 1)));

        assertEquals(0, repository.findAll().size(), "the purchase row must not survive a failed transaction");
        assertEquals(5, inventoryQuantity(4), "inventory must be untouched when the transaction rolls back");
    }

    @Test
    void spendingTotalAndSpendingBetweenSumPricePaid() throws SQLException {
        seedInventoryItem(5, 0, null);
        seedStoreItem(5, 5);

        service.recordPurchase(5, null, false, LocalDate.of(2026, 9, 1), 1, 2.00, LocalDate.of(2027, 1, 1));
        service.recordPurchase(5, null, false, LocalDate.of(2026, 9, 15), 1, 3.50, LocalDate.of(2027, 1, 1));
        service.recordPurchase(5, null, false, LocalDate.of(2026, 10, 5), 1, 10.00, LocalDate.of(2027, 1, 1));

        assertEquals(15.50, service.spendingTotal(), 0.001);
        assertEquals(5.50, service.spendingBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), 0.001,
                "only the two September purchases should count");
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

    private void seedInventoryItem(int id, int quantity, String earliestExpDate) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, " +
                             "inventory_item_quantity, inventory_item_unit, inventory_item_category, " +
                             "inventory_item_earliest_exp_date) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, "Test Item " + id);
            ps.setInt(3, quantity);
            ps.setString(4, "Stück");
            ps.setString(5, "Sonstiges");
            ps.setString(6, earliestExpDate);
            ps.executeUpdate();
        }
    }

    /** A store_item linked to the given inventory_item, the way store_item_item_id links to inventory_item. */
    private void seedStoreItem(int storeItemId, int inventoryItemId) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute("INSERT INTO store VALUES (1, 'Aldi')");
            st.execute("INSERT INTO brand VALUES (1, 'TestBrand')");
        }
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, " +
                             "store_item_item_id, store_item_package_unit, store_item_package_size, " +
                             "store_item_price, store_item_is_available, store_item_deposit) " +
                             "VALUES (?, 1, 1, ?, 'Stück', 1, 1.00, 1, NULL)")) {
            ps.setInt(1, storeItemId);
            ps.setInt(2, inventoryItemId);
            ps.executeUpdate();
        }
    }

    private void createSchema() throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute("CREATE TABLE inventory_item (" +
                    "inventory_item_id INTEGER PRIMARY KEY, " +
                    "inventory_item_name_english TEXT NOT NULL, " +
                    "inventory_item_quantity INTEGER NOT NULL DEFAULT 0, " +
                    "inventory_item_unit TEXT NOT NULL, " +
                    "inventory_item_category TEXT NOT NULL, " +
                    "inventory_item_earliest_exp_date DATE)");

            st.execute("CREATE TABLE store (" +
                    "store_id INTEGER PRIMARY KEY, " +
                    "store_name TEXT NOT NULL)");

            st.execute("CREATE TABLE brand (" +
                    "brand_id INTEGER PRIMARY KEY, " +
                    "brand_name TEXT NOT NULL)");

            st.execute("CREATE TABLE store_item (" +
                    "store_item_id INTEGER PRIMARY KEY, " +
                    "store_item_brand_id INTEGER NOT NULL REFERENCES brand(brand_id), " +
                    "store_item_store_id INTEGER NOT NULL REFERENCES store(store_id), " +
                    "store_item_item_id INTEGER NOT NULL REFERENCES inventory_item(inventory_item_id), " +
                    "store_item_package_unit TEXT NOT NULL, " +
                    "store_item_package_size INTEGER NOT NULL, " +
                    "store_item_price REAL, " +
                    "store_item_is_available BOOLEAN NOT NULL, " +
                    "store_item_deposit REAL)");

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
