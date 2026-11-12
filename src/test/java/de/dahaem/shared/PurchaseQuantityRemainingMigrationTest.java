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

import static de.dahaem.testsupport.TestSql.count;
import static de.dahaem.testsupport.TestSql.exec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * migrations/0018_purchase_quantity_remaining.sql on a throwaway MariaDB, starting from purchase_history as it was before
 * it: existing purchases are untouched (remaining stays NULL = all of it left), it can be run twice, and the migrated
 * table matches a fresh schema.sql.
 */
class PurchaseQuantityRemainingMigrationTest {

    private static final Path OLD_TABLE = Path.of("src", "test", "resources", "migrations", "pre_0018_purchase_history.sql");
    private static final Path MIGRATION = Path.of("migrations", "0018_purchase_quantity_remaining.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void keepsExistingPurchasesUntouchedAndIsReRunnable() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLE));
        exec("INSERT INTO store_item (store_item_id) VALUES (1)");
        exec("INSERT INTO purchase_history (purchase_history_store_item_id, purchase_history_was_discounted, "
                + "purchase_history_purchase_date, purchase_history_quantity_purchased, purchase_history_price_paid, "
                + "purchase_history_expiration_date) VALUES (1, FALSE, '2026-11-01', 3, 1.00, '2026-12-01')");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // and once more: re-runnable

        assertEquals(1, count("SELECT COUNT(*) FROM purchase_history WHERE purchase_history_quantity_purchased = 3 "
                + "AND purchase_history_quantity_remaining IS NULL"));
    }

    @Test
    void migratedTableMatchesAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = columnsOfPurchaseHistory();

        TestDatabase.reset(List.of(OLD_TABLE, MIGRATION));
        List<String> migrated = columnsOfPurchaseHistory();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    // ---------- helpers ----------

    private static List<String> columnsOfPurchaseHistory() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT CONCAT(COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', IS_NULLABLE, "
                     + "' default=', IFNULL(COLUMN_DEFAULT, 'NULL')) FROM information_schema.COLUMNS "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_history' "
                     // purchase_history_household_id comes from migration 0024, which has its own test
                     + "AND COLUMN_NAME <> 'purchase_history_household_id' ORDER BY COLUMN_NAME")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
