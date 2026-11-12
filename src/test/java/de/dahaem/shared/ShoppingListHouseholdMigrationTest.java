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
 * migrations/0023_shopping_list_household.sql on a throwaway MariaDB, starting from shopping_list as it was before it: a
 * list made from a plan goes to the household of that plan, every other list to the oldest household, it can be run
 * twice, the foreign key holds, and the migrated table matches a fresh schema.sql.
 */
class ShoppingListHouseholdMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0023_shopping_list.sql");
    private static final Path MIGRATION = Path.of("migrations", "0023_shopping_list_household.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void listsGoToTheHouseholdOfTheirPlanOrToTheOldestHousehold() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (3), (5)");
        exec("INSERT INTO meal_plan (meal_plan_id, meal_plan_household_id, meal_plan_week_start_date) VALUES "
                + "(1, 3, '2026-11-02'), (2, 5, '2026-11-02')");
        exec("INSERT INTO shopping_list (shopping_list_id, shopping_list_meal_plan_id, shopping_list_created_date, "
                + "shopping_list_status) VALUES (10, 1, '2026-11-01', FALSE), (11, 2, '2026-11-01', FALSE), "
                + "(12, NULL, '2026-11-01', FALSE)");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // and once more: re-runnable

        assertEquals(3, household(10));
        assertEquals(5, household(11));
        assertEquals(3, household(12), "a list without a plan goes to the oldest household (the lowest id)");
        assertEquals(0, count("SELECT COUNT(*) FROM shopping_list WHERE shopping_list_household_id IS NULL"));
        // the foreign key holds
        assertThrows(SQLException.class, () -> exec("INSERT INTO shopping_list (shopping_list_household_id, "
                + "shopping_list_created_date, shopping_list_status) VALUES (99, '2026-11-02', FALSE)"));
    }

    @Test
    void aDatabaseWithoutAnyHouseholdMigratesToo() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));

        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(0, count("SELECT COUNT(*) FROM shopping_list"));
    }

    @Test
    void migratedTableMatchesAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = describeShoppingList();

        TestDatabase.reset(List.of(OLD_TABLES, MIGRATION));
        List<String> migrated = describeShoppingList();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    // ---------- helpers ----------

    private static int household(int listId) throws SQLException {
        return count("SELECT shopping_list_household_id FROM shopping_list WHERE shopping_list_id = " + listId);
    }

    private static List<String> describeShoppingList() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('column ', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                    + "IS_NULLABLE, ' default=', IFNULL(COLUMN_DEFAULT, 'NULL')) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list' ORDER BY COLUMN_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('constraint ', CONSTRAINT_NAME, ' ', CONSTRAINT_TYPE) "
                    + "FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'shopping_list' AND CONSTRAINT_TYPE = 'FOREIGN KEY' "
                    + "AND CONSTRAINT_NAME = 'shopping_list_household_fk' ORDER BY CONSTRAINT_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }
}
