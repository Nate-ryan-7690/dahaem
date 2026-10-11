package de.dahaem.stores;

import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * saveOffers records price changes of an already-stored offer in store_offer_price_change (same
 * transaction as the upsert), and migrations/0007 creates the same table as schema.sql. Runs on a
 * throwaway MariaDB (Testcontainers), never the live database.
 */
class OfferPriceHistoryTest {

    private static final Path MIGRATION = Path.of("migrations", "0007_offer_price_history.sql");
    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);   // Monday of the offer week
    private static final LocalDate UNTIL = LocalDate.of(2026, 10, 11);

    private final StoreRepository stores = new StoreRepository();
    private int storeId;

    @BeforeEach
    void setUp() {
        TestDatabase.reset();
        storeId = stores.findOrCreateStore("Penny");
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    @Test
    void unchangedRePullWritesNoChangeRow() throws SQLException {
        StoreOffer deal = new StoreOffer("A1", "Brand", "Cola", "1,5 l", 1.29, 2, 2.22, 1.79, null, null, 0.25,
                null, "Getränke", "R1", FROM, UNTIL);
        stores.saveOffers(storeId, List.of(deal, offer("B1", 0.99, null)), FROM, "STORE");
        stores.saveOffers(storeId, List.of(deal, offer("B1", 0.99, null)), FROM.plusDays(1), "STORE");

        assertEquals(0, count("SELECT COUNT(*) FROM store_offer_price_change"));
        assertEquals(2, count("SELECT COUNT(*) FROM store_offer"));
    }

    @Test
    void newOfferWritesNoChangeRow() throws SQLException {
        stores.saveOffers(storeId, List.of(offer("A1", 1.99, 2.49)), FROM, "STORE");
        // same external id in the NEXT week is a new offer (valid_from is part of the key), not a change
        stores.saveOffers(storeId, List.of(new StoreOffer("A1", "Brand", "Milch", "1 l", 1.49, 1, null, 2.49,
                null, null, null, null, "Milch", "", FROM.plusDays(7), UNTIL.plusDays(7))), FROM.plusDays(7), "STORE");

        assertEquals(0, count("SELECT COUNT(*) FROM store_offer_price_change"));
        assertEquals(2, count("SELECT COUNT(*) FROM store_offer"));
    }

    @Test
    void changedSalePriceWritesOneRowWithOldAndNew() throws SQLException {
        stores.saveOffers(storeId, List.of(offer("A1", 1.99, 2.49)), FROM, "STORE");
        stores.saveOffers(storeId, List.of(offer("A1", 1.49, 2.49)), FROM.plusDays(2), "STORE");

        List<String> changes = changes();
        assertEquals(List.of("A1 sale 1.99->1.49 normal 2.49->2.49 deal null->null on 2026-10-07"), changes);
        assertEquals(new BigDecimal("1.49"), single("SELECT store_offer_sale_price FROM store_offer"),
                "the offer itself is still updated in place");
    }

    @Test
    void normalAndDealPriceChangesAreRecordedToo() throws SQLException {
        stores.saveOffers(storeId, List.of(offer("A1", 1.99, null)), FROM, "STORE");
        stores.saveOffers(storeId, List.of(offer("A1", 1.99, 2.49)), FROM.plusDays(1), "STORE");
        stores.saveOffers(storeId, List.of(new StoreOffer("A1", "Brand", "Milch", "1 l", 1.99, 3, 5.00, 2.49,
                null, null, null, null, "Milch", "", FROM, UNTIL)), FROM.plusDays(2), "STORE");

        assertEquals(List.of(
                "A1 sale 1.99->1.99 normal null->2.49 deal null->null on 2026-10-06",
                "A1 sale 1.99->1.99 normal 2.49->2.49 deal null->5.00 on 2026-10-07"), changes());
    }

    @Test
    void twoChangesInOneWeekWriteTwoRowsInOrder() throws SQLException {
        stores.saveOffers(storeId, List.of(offer("A1", 1.99, 2.49)), FROM, "STORE");
        stores.saveOffers(storeId, List.of(offer("A1", 1.49, 2.49)), FROM.plusDays(1), "STORE");
        stores.saveOffers(storeId, List.of(offer("A1", 1.49, 2.49)), FROM.plusDays(2), "STORE");  // unchanged
        stores.saveOffers(storeId, List.of(offer("A1", 1.29, 2.49)), FROM.plusDays(3), "STORE");

        assertEquals(List.of(
                "A1 sale 1.99->1.49 normal 2.49->2.49 deal null->null on 2026-10-06",
                "A1 sale 1.49->1.29 normal 2.49->2.49 deal null->null on 2026-10-08"), changes());
        assertEquals(1, count("SELECT COUNT(*) FROM store_offer"));
    }

    @Test
    void failedSaveLeavesNoPartialRows() throws SQLException {
        stores.saveOffers(storeId, List.of(offer("A1", 1.99, 2.49)), FROM, "STORE");
        // A1's price changed (would write a change row), then B1 fails: name is NOT NULL.
        StoreOffer broken = new StoreOffer("B1", "Brand", null, "1 l", 0.99, 1, null, null, null, null, null,
                null, "Milch", "", FROM, UNTIL);

        assertThrows(RuntimeException.class, () -> stores.saveOffers(storeId,
                List.of(offer("A1", 1.49, 2.49), broken), FROM.plusDays(1), "STORE"));

        assertEquals(0, count("SELECT COUNT(*) FROM store_offer_price_change"));
        assertEquals(1, count("SELECT COUNT(*) FROM store_offer"));
        assertEquals(new BigDecimal("1.99"), single("SELECT store_offer_sale_price FROM store_offer"),
                "the offer update was rolled back with the change row");
    }

    @Test
    void migrationIsReRunnableAndMatchesAFreshSchema() throws SQLException {
        List<String> fresh = changeTableColumns();

        exec("DROP TABLE store_offer_price_change");   // scratch DB only: schema as it was before 0007
        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));        // re-runnable

        assertEquals(fresh, changeTableColumns());
        assertEquals(10, fresh.size());
    }

    // ---------- helpers ----------

    private static StoreOffer offer(String id, double sale, Double normal) {
        return new StoreOffer(id, "Brand", "Milch", "1 l", sale, 1, null, normal, null, null, null,
                null, "Milch", "", FROM, UNTIL);
    }

    private static List<String> changes() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT o.store_offer_external_id, "
                     + "c.store_offer_price_change_old_sale_price, c.store_offer_price_change_new_sale_price, "
                     + "c.store_offer_price_change_old_normal_price, c.store_offer_price_change_new_normal_price, "
                     + "c.store_offer_price_change_old_deal_price, c.store_offer_price_change_new_deal_price, "
                     + "c.store_offer_price_change_observed_on "
                     + "FROM store_offer_price_change c JOIN store_offer o "
                     + "  ON o.store_offer_id = c.store_offer_price_change_offer_id "
                     + "ORDER BY c.store_offer_price_change_observed_on, c.store_offer_price_change_id")) {
            while (rs.next()) {
                out.add(rs.getString(1) + " sale " + rs.getBigDecimal(2) + "->" + rs.getBigDecimal(3)
                        + " normal " + rs.getBigDecimal(4) + "->" + rs.getBigDecimal(5)
                        + " deal " + rs.getBigDecimal(6) + "->" + rs.getBigDecimal(7)
                        + " on " + rs.getObject(8, LocalDate.class));
            }
        }
        return out;
    }

    private static List<String> changeTableColumns() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT "
                     + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                     + "AND TABLE_NAME = 'store_offer_price_change' ORDER BY ORDINAL_POSITION")) {
            while (rs.next()) {
                out.add(rs.getString(1) + " " + rs.getString(2) + " null=" + rs.getString(3)
                        + " default=" + rs.getString(4));
            }
        }
        return out;
    }

    private static BigDecimal single(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private static int count(String sql) throws SQLException {
        return single(sql).intValue();
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
