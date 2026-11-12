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
 * migrations/0025_household_invite.sql on a throwaway MariaDB: it adds the table and nothing else, it can be run twice, the
 * code hash is unique, the foreign keys hold, and the table matches a fresh schema.sql (columns, indexes, foreign keys).
 */
class HouseholdInviteMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0025_household_invite.sql");
    private static final Path MIGRATION = Path.of("migrations", "0025_household_invite.sql");
    private static final String HASH_A = "a".repeat(64);

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void addsTheTableCanBeRunTwiceAndKeepsExistingRows() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (1)");
        exec("INSERT INTO app_user (user_id) VALUES (1)");

        TestDatabase.apply(List.of(MIGRATION));
        exec(insert(HASH_A));
        TestDatabase.apply(List.of(MIGRATION));   // and once more: the invitation is still there

        assertEquals(1, count("SELECT COUNT(*) FROM household_invite"));
        assertEquals(1, count("SELECT COUNT(*) FROM household"));
        assertEquals(1, count("SELECT COUNT(*) FROM app_user"));
    }

    @Test
    void theCodeHashIsUniqueAndTheForeignKeysHold() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (1)");
        exec("INSERT INTO app_user (user_id) VALUES (1)");
        TestDatabase.apply(List.of(MIGRATION));
        exec(insert(HASH_A));

        assertThrows(SQLException.class, () -> exec(insert(HASH_A)));
        assertThrows(SQLException.class, () -> exec(insert("b".repeat(64)).replace("(1, '", "(99, '")));
        assertThrows(SQLException.class, () -> exec("UPDATE household_invite SET household_invite_used_by = 99"));
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

    // ---------- helpers ----------

    private static String insert(String hash) {
        return "INSERT INTO household_invite (household_invite_household_id, household_invite_code_hash, "
                + "household_invite_code_hint, household_invite_created_by, household_invite_created_at, "
                + "household_invite_expires_at) VALUES (1, '" + hash + "', 'ABCD', 1, '2026-11-11 10:00:00', "
                + "'2026-11-18 10:00:00')";
    }

    private static List<String> describe() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('column ', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                    + "IS_NULLABLE) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'household_invite' ORDER BY COLUMN_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('index ', INDEX_NAME, ' ', SEQ_IN_INDEX, ' ', COLUMN_NAME, "
                    + "' unique=', IF(NON_UNIQUE = 0, 'yes', 'no')) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'household_invite' "
                    + "AND INDEX_NAME NOT LIKE 'household_invite_%_fk' ORDER BY INDEX_NAME, SEQ_IN_INDEX")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('constraint ', CONSTRAINT_NAME, ' ', CONSTRAINT_TYPE) "
                    + "FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'household_invite' ORDER BY CONSTRAINT_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }
}
