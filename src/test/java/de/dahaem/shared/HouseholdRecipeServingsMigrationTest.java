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
 * migrations/0026_household_recipe_servings.sql on a throwaway MariaDB: every preference set today goes to the oldest
 * household, the old column stays, it can be run twice (a second run does not overwrite a changed preference), the
 * foreign keys and the "at least 1" check hold, and the table matches a fresh schema.sql.
 */
class HouseholdRecipeServingsMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0026_household_recipe_servings.sql");
    private static final Path MIGRATION = Path.of("migrations", "0026_household_recipe_servings.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void preferencesGoToTheOldestHouseholdAndTheOldColumnStays() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (3), (5)");
        exec("INSERT INTO recipe (recipe_id, recipe_preferred_servings) VALUES (1, 4), (2, NULL), (3, 6)");

        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(2, count("SELECT COUNT(*) FROM household_recipe_servings WHERE household_recipe_servings_household_id = 3"));
        assertEquals(4, count("SELECT household_recipe_servings_servings FROM household_recipe_servings "
                + "WHERE household_recipe_servings_recipe_id = 1"));
        assertEquals(0, count("SELECT COUNT(*) FROM household_recipe_servings WHERE household_recipe_servings_recipe_id = 2"));
        assertEquals(0, count("SELECT COUNT(*) FROM household_recipe_servings WHERE household_recipe_servings_household_id = 5"));
        assertEquals(4, count("SELECT recipe_preferred_servings FROM recipe WHERE recipe_id = 1"), "the old column is untouched");
    }

    @Test
    void aSecondRunDoesNotOverwriteAChangedPreference() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (3)");
        exec("INSERT INTO recipe (recipe_id, recipe_preferred_servings) VALUES (1, 4)");
        TestDatabase.apply(List.of(MIGRATION));
        exec("UPDATE household_recipe_servings SET household_recipe_servings_servings = 8");

        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(8, count("SELECT household_recipe_servings_servings FROM household_recipe_servings"));
        assertEquals(1, count("SELECT COUNT(*) FROM household_recipe_servings"));
    }

    @Test
    void theKeysAndTheCheckHold() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (3)");
        exec("INSERT INTO recipe (recipe_id) VALUES (1)");
        TestDatabase.apply(List.of(MIGRATION));

        assertThrows(SQLException.class, () -> exec(insert(99, 1, 4)));
        assertThrows(SQLException.class, () -> exec(insert(3, 99, 4)));
        assertThrows(SQLException.class, () -> exec(insert(3, 1, 0)));
        exec(insert(3, 1, 4));
        assertThrows(SQLException.class, () -> exec(insert(3, 1, 5)), "one preference per household and recipe");
    }

    @Test
    void aDatabaseWithoutAnyHouseholdMigratesToo() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO recipe (recipe_id, recipe_preferred_servings) VALUES (1, 4)");

        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(0, count("SELECT COUNT(*) FROM household_recipe_servings"));
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

    private static String insert(int household, int recipe, int servings) {
        return "INSERT INTO household_recipe_servings (household_recipe_servings_household_id, "
                + "household_recipe_servings_recipe_id, household_recipe_servings_servings) VALUES ("
                + household + ", " + recipe + ", " + servings + ")";
    }

    private static List<String> describe() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('column ', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                    + "IS_NULLABLE) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'household_recipe_servings' ORDER BY COLUMN_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('constraint ', CONSTRAINT_NAME, ' ', CONSTRAINT_TYPE) "
                    + "FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'household_recipe_servings' ORDER BY CONSTRAINT_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }
}
