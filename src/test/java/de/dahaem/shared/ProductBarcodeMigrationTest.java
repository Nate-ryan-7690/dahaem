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
 * migrations/0020_product_barcode.sql on a throwaway MariaDB: it creates both barcode tables, can be run twice, the
 * foreign keys hold (a barcode needs a real item, a link needs a real barcode and a real store item), and the migrated
 * tables match a fresh schema.sql.
 */
class ProductBarcodeMigrationTest {

    private static final Path STUBS = Path.of("src", "test", "resources", "migrations", "pre_0020_tables.sql");
    private static final Path MIGRATION = Path.of("migrations", "0020_product_barcode.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void createsTheTablesIsReRunnableAndEnforcesItsForeignKeys() throws SQLException {
        TestDatabase.reset(List.of(STUBS));
        exec("INSERT INTO inventory_item (inventory_item_id) VALUES (1)");
        exec("INSERT INTO store_item (store_item_id) VALUES (1)");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // and once more: re-runnable

        exec("INSERT INTO product_barcode (product_barcode_code, product_barcode_item_id, product_barcode_package_unit, "
                + "product_barcode_package_size, product_barcode_created_date) VALUES ('4006381333931', 1, 'g', 500, '2026-11-11')");
        exec("INSERT INTO product_barcode_store_item VALUES ('4006381333931', 1)");
        assertEquals(1, count("SELECT COUNT(*) FROM product_barcode_store_item"));

        assertThrows(SQLException.class, () -> exec("INSERT INTO product_barcode (product_barcode_code, "
                + "product_barcode_item_id, product_barcode_package_unit, product_barcode_package_size, "
                + "product_barcode_created_date) VALUES ('4006381333948', 99, 'g', 500, '2026-11-11')"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO product_barcode_store_item VALUES ('4006381333931', 99)"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO product_barcode_store_item VALUES ('0000000000000', 1)"));
    }

    @Test
    void migratedTablesMatchAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = columnsOfTheBarcodeTables();

        TestDatabase.reset(List.of(STUBS, MIGRATION));
        List<String> migrated = columnsOfTheBarcodeTables();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    // ---------- helpers ----------

    private static List<String> columnsOfTheBarcodeTables() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                     + "IS_NULLABLE, ' default=', IFNULL(COLUMN_DEFAULT, 'NULL')) FROM information_schema.COLUMNS "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('product_barcode', 'product_barcode_store_item') "
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
