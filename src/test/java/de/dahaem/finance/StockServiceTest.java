package de.dahaem.finance;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.Item;
import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import de.dahaem.testsupport.TestHousehold;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static de.dahaem.testsupport.TestSql.exec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Using / throwing away stock on a throwaway MariaDB: the amount leaves the batch that expires first, the item's quantity
 * and earliest expiry date follow, too much is refused, and stock no purchase explains is only counted down.
 */
class StockServiceTest {

    private PurchaseService purchases;
    private StockService stock;
    private int household;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        PurchaseRepository repository = new PurchaseRepository();
        purchases = new PurchaseService(repository);
        stock = new StockService(repository, new InventoryRepository());
        household = TestHousehold.defaultHousehold();
        exec("INSERT INTO store (store_id, store_name) VALUES (1, 'Aldi')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (1, 'TestBrand')");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_quantity, "
                + "inventory_item_unit, inventory_item_category) VALUES (1, 'Milk', 0, 'Stück', 'Dairy')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, "
                + "store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available) "
                + "VALUES (1, 1, 1, 1, 'Stück', 1, 1.00, 1)");
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void takesFromTheEarliestExpiringBatchFirstAndMovesTheDateOn() throws SQLException {
        buy(2, "2026-12-10");   // bought first, expires later
        buy(3, "2026-12-01");   // bought second, expires first

        Item item = stock.use(household, 1, 4).orElseThrow();

        assertEquals(1, item.getQuantity());
        assertEquals(LocalDate.of(2026, 12, 10), item.getEarliestExpDate(),
                "the 01.12. batch is used up, so the date moves to the 10.12. batch");
        assertEquals(0, remaining("2026-12-01"));
        assertEquals(1, remaining("2026-12-10"));
        assertEquals(1, storedQuantity());
        assertEquals("2026-12-10", storedDate());
    }

    @Test
    void usingPartOfTheEarliestBatchKeepsItsDate() throws SQLException {
        buy(3, "2026-12-01");
        buy(2, "2026-12-10");

        Item item = stock.use(household, 1, 1).orElseThrow();

        assertEquals(4, item.getQuantity());
        assertEquals(LocalDate.of(2026, 12, 1), item.getEarliestExpDate());
        assertEquals(2, remaining("2026-12-01"));
    }

    @Test
    void emptyingTheItemClearsTheDate() throws SQLException {
        buy(2, "2026-12-01");

        Item item = stock.use(household, 1, 2).orElseThrow();

        assertEquals(0, item.getQuantity());
        assertNull(item.getEarliestExpDate());
        assertNull(storedDate());
    }

    @Test
    void moreThanInStockIsRefusedAndNothingChanges() throws SQLException {
        buy(2, "2026-12-01");

        assertThrows(IllegalArgumentException.class, () -> stock.use(household, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> stock.use(household, 1, 0));

        assertEquals(2, storedQuantity());
        assertEquals(2, remaining("2026-12-01"));
    }

    @Test
    void unknownItemIsEmpty() {
        assertTrue(stock.use(household, 999, 1).isEmpty());
    }

    @Test
    void stockWithoutAPurchaseIsOnlyCountedDownAndKeepsItsDate() throws SQLException {
        TestHousehold.setStock(household, 1, 5, "2026-12-05");

        Item item = stock.use(household, 1, 2).orElseThrow();

        assertEquals(3, item.getQuantity());
        assertEquals(LocalDate.of(2026, 12, 5), item.getEarliestExpDate());
    }

    // ---------- helpers ----------

    private void buy(int quantity, String expires) {
        purchases.recordPurchase(household, 1, null, false, LocalDate.of(2026, 11, 10), quantity, 1.00, LocalDate.parse(expires));
    }

    /** What is left of the purchase that expires on the given day; NULL (untouched) counts as all of it. */
    private int remaining(String expires) throws SQLException {
        return queryInt("SELECT COALESCE(purchase_history_quantity_remaining, purchase_history_quantity_purchased) "
                + "FROM purchase_history WHERE purchase_history_expiration_date = '" + expires + "'");
    }

    private int storedQuantity() throws SQLException {
        return queryInt("SELECT COALESCE(SUM(household_stock_quantity), 0) FROM household_stock "
                + "WHERE household_stock_item_id = 1 AND household_stock_household_id = " + household);
    }

    private String storedDate() throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT MIN(household_stock_earliest_exp_date) FROM household_stock "
                     + "WHERE household_stock_item_id = 1 AND household_stock_household_id = " + household)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
