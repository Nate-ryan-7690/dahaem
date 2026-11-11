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

/**
 * migrations/0019_shopping_list_archive_and_free_text.sql on a throwaway MariaDB, starting from the shopping list tables
 * as they were before it: existing lists are not archived and existing lines have no name, it can be run twice, a
 * free-text line fits, and the migrated tables match a fresh schema.sql.
 */
class ShoppingListArchiveMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0019_shopping_list_tables.sql");
    private static final Path MIGRATION = Path.of("migrations", "0019_shopping_list_archive_and_free_text.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void keepsExistingRowsGivesTheNewColumnsTheirDefaultsAndIsReRunnable() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO shopping_list (shopping_list_id, shopping_list_created_date, shopping_list_status) "
                + "VALUES (1, '2026-11-01', FALSE)");
        exec("INSERT INTO shopping_list_item (shopping_list_id, shopping_list_item_quantity_needed, "
                + "shopping_list_item_purchase_status, shopping_list_item_is_manual) VALUES (1, 3, FALSE, FALSE)");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // and once more: re-runnable

        assertEquals(1, count("SELECT COUNT(*) FROM shopping_list WHERE shopping_list_archived = FALSE"));
        assertEquals(1, count("SELECT COUNT(*) FROM shopping_list_item WHERE shopping_list_item_name IS NULL "
                + "AND shopping_list_item_quantity_needed = 3.000"));
        // a free-text line has a name and no catalogue item
        exec("INSERT INTO shopping_list_item (shopping_list_id, shopping_list_item_quantity_needed, "
                + "shopping_list_item_name, shopping_list_item_purchase_status, shopping_list_item_is_manual) "
                + "VALUES (1, 1, 'Backpapier', FALSE, TRUE)");
        assertEquals(1, count("SELECT COUNT(*) FROM shopping_list_item WHERE shopping_list_item_name = 'Backpapier' "
                + "AND shopping_list_item_inventory_item_id IS NULL"));
    }

    @Test
    void migratedTablesMatchAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = columnsOfTheShoppingTables();

        TestDatabase.reset(List.of(OLD_TABLES, MIGRATION));
        List<String> migrated = columnsOfTheShoppingTables();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    // ---------- helpers ----------

    private static List<String> columnsOfTheShoppingTables() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                     + "IS_NULLABLE, ' default=', IFNULL(COLUMN_DEFAULT, 'NULL')) FROM information_schema.COLUMNS "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN "
                     + "('shopping_list', 'shopping_list_item', 'shopping_list_item_recipe') "
                     // shopping_list_household_id comes from migration 0023, which has its own test
                     + "AND COLUMN_NAME <> 'shopping_list_household_id' "
                     + "ORDER BY TABLE_NAME, COLUMN_NAME")) {
            while (rs.next()) {
                out.add(rs.getString(1));
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

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
