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

/** migrations/0021_intake_request.sql on a throwaway MariaDB: creates the table, can be run twice, matches schema.sql. */
class IntakeRequestMigrationTest {

    private static final Path MIGRATION = Path.of("migrations", "0021_intake_request.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void createsTheTableIsReRunnableAndTheRequestIdIsUnique() throws SQLException {
        TestDatabase.reset(List.of());
        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // and once more: re-runnable

        exec("INSERT INTO intake_request VALUES ('req-0001', REPEAT('a', 64), NOW(), NULL)");
        assertEquals(1, count("SELECT COUNT(*) FROM intake_request WHERE intake_request_result IS NULL"));
        assertThrows(SQLException.class,
                () -> exec("INSERT INTO intake_request VALUES ('req-0001', REPEAT('b', 64), NOW(), NULL)"));
    }

    @Test
    void migratedTableMatchesAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = columns();

        TestDatabase.reset(List.of());
        TestDatabase.apply(List.of(MIGRATION));
        List<String> migrated = columns();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    private static List<String> columns() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT CONCAT(COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', IS_NULLABLE, "
                     + "' default=', IFNULL(COLUMN_DEFAULT, 'NULL')) FROM information_schema.COLUMNS "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'intake_request' ORDER BY COLUMN_NAME")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
