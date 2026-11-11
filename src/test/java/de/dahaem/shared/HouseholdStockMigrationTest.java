package de.dahaem.shared;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * migrations/0024_household_stock.sql on a throwaway MariaDB, starting from the tables as they were before it: the stock of
 * every item that has some is copied to the oldest household, every purchase goes to that household, nothing is deleted
 * and the old columns stay, it can be run twice (a second run does not overwrite stock that changed in between), the
 * foreign keys hold, and the new table and column match a fresh schema.sql.
 */
class HouseholdStockMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0024_household_stock.sql");
    private static final Path MIGRATION = Path.of("migrations", "0024_household_stock.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void stockAndPurchasesGoToTheOldestHouseholdAndNothingIsLost() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (3), (5)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_quantity, "
                + "inventory_item_unit, inventory_item_category, inventory_item_earliest_exp_date) VALUES "
                + "(1, 'milk', 2000, 'ml', 'dairy', '2026-12-01'), (2, 'salt', 0, 'g', 'spices', NULL), "
                + "(3, 'flour', 500, 'g', 'baking', NULL)");
        exec("INSERT INTO purchase_history (purchase_history_store_item_id, purchase_history_purchase_date, "
                + "purchase_history_price_paid) VALUES (1, '2026-11-01', 1.19), (1, '2026-11-02', 2.38)");

        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(2, count("SELECT COUNT(*) FROM household_stock WHERE household_stock_household_id = 3"),
                "milk and flour have stock, salt has none");
        assertEquals(2000, count("SELECT household_stock_quantity FROM household_stock WHERE household_stock_item_id = 1"));
        assertEquals("2026-12-01", text("SELECT household_stock_earliest_exp_date FROM household_stock "
                + "WHERE household_stock_item_id = 1"));
        assertEquals(0, count("SELECT COUNT(*) FROM household_stock WHERE household_stock_item_id = 2"));
        assertEquals(0, count("SELECT COUNT(*) FROM household_stock WHERE household_stock_household_id = 5"));
        assertEquals(2, count("SELECT COUNT(*) FROM purchase_history WHERE purchase_history_household_id = 3"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_history WHERE purchase_history_household_id IS NULL"));
        // the old columns are untouched
        assertEquals(2000, count("SELECT inventory_item_quantity FROM inventory_item WHERE inventory_item_id = 1"));
        assertEquals(3, count("SELECT COUNT(*) FROM inventory_item"));
    }

    @Test
    void aSecondRunChangesNothingAndDoesNotOverwriteStockThatChanged() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (3)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_quantity, "
                + "inventory_item_unit, inventory_item_category) VALUES (1, 'milk', 2000, 'ml', 'dairy')");
        TestDatabase.apply(List.of(MIGRATION));
        exec("UPDATE household_stock SET household_stock_quantity = 700 WHERE household_stock_item_id = 1");   // used since

        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(700, count("SELECT household_stock_quantity FROM household_stock WHERE household_stock_item_id = 1"));
        assertEquals(1, count("SELECT COUNT(*) FROM household_stock"));
    }

    @Test
    void theForeignKeysHold() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (3)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_unit, "
                + "inventory_item_category) VALUES (1, 'milk', 'ml', 'dairy')");
        TestDatabase.apply(List.of(MIGRATION));

        assertThrows(SQLException.class, () -> exec("INSERT INTO household_stock (household_stock_household_id, "
                + "household_stock_item_id, household_stock_quantity) VALUES (99, 1, 1)"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO household_stock (household_stock_household_id, "
                + "household_stock_item_id, household_stock_quantity) VALUES (3, 99, 1)"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO purchase_history (purchase_history_household_id, "
                + "purchase_history_store_item_id, purchase_history_purchase_date, purchase_history_price_paid) "
                + "VALUES (99, 1, '2026-11-01', 1.00)"));
    }

    @Test
    void aDatabaseWithoutAnyHouseholdMigratesToo() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_quantity, "
                + "inventory_item_unit, inventory_item_category) VALUES (1, 'milk', 2000, 'ml', 'dairy')");

        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(0, count("SELECT COUNT(*) FROM household_stock"));
    }

    @Test
    void migratedTablesMatchAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = describe();

        TestDatabase.reset(List.of(OLD_TABLES, MIGRATION));
        List<String> migrated = describe();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    // ---------- helpers ----------

    /** household_stock completely, and the household column and key of purchase_history. */
    private static List<String> describe() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                    + "IS_NULLABLE, ' default=', IFNULL(COLUMN_DEFAULT, 'NULL')) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND (TABLE_NAME = 'household_stock' "
                    + "OR (TABLE_NAME = 'purchase_history' AND COLUMN_NAME = 'purchase_history_household_id')) "
                    + "ORDER BY TABLE_NAME, COLUMN_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONCAT(TABLE_NAME, ' ', CONSTRAINT_NAME, ' ', CONSTRAINT_TYPE) "
                    + "FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() "
                    + "AND (TABLE_NAME = 'household_stock' OR CONSTRAINT_NAME = 'purchase_history_household_fk') "
                    + "ORDER BY TABLE_NAME, CONSTRAINT_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String text(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
