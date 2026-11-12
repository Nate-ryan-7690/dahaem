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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * migrations/0027_household_equipment.sql on a throwaway MariaDB: it only adds the table, it can be run twice and keeps
 * its rows, one row per household and equipment, the foreign key holds, and the table matches a fresh schema.sql.
 */
class HouseholdEquipmentMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0027_household_equipment.sql");
    private static final Path MIGRATION = Path.of("migrations", "0027_household_equipment.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void addsTheTableAndKeepsItsRowsWhenRunTwice() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (1), (2)");

        TestDatabase.apply(List.of(MIGRATION));
        exec("INSERT INTO household_unavailable_equipment VALUES (1, 'thermomix')");
        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(1, count("SELECT COUNT(*) FROM household_unavailable_equipment"));
        assertEquals(2, count("SELECT COUNT(*) FROM household"));
    }

    @Test
    void oneRowPerHouseholdAndEquipmentAndTheForeignKeyHolds() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (1)");
        TestDatabase.apply(List.of(MIGRATION));
        exec("INSERT INTO household_unavailable_equipment VALUES (1, 'thermomix')");

        assertThrows(SQLException.class, () -> exec("INSERT INTO household_unavailable_equipment VALUES (1, 'thermomix')"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO household_unavailable_equipment VALUES (99, 'thermomix')"));
    }

    @Test
    void theMigratedTableMatchesAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = describe();

        TestDatabase.reset(List.of(OLD_TABLES, MIGRATION));
        List<String> migrated = describe();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    private static List<String> describe() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('column ', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                    + "IS_NULLABLE) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'household_unavailable_equipment' ORDER BY COLUMN_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('constraint ', CONSTRAINT_NAME, ' ', CONSTRAINT_TYPE) "
                    + "FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'household_unavailable_equipment' ORDER BY CONSTRAINT_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }
}
