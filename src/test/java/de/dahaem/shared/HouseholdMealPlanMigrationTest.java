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
 * migrations/0022_meal_plan_household.sql on a throwaway MariaDB, starting from meal_plan as it was before it: every
 * existing plan goes to the oldest household and keeps its week, it can be run twice, a household has one plan per week
 * but two households can plan the same week, and the migrated table matches a fresh schema.sql (columns, indexes,
 * foreign keys).
 */
class HouseholdMealPlanMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0022_meal_plan.sql");
    private static final Path MIGRATION = Path.of("migrations", "0022_meal_plan_household.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void existingPlansGoToTheOldestHouseholdAndTheWeekIsUniquePerHousehold() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_id) VALUES (3), (5)");
        exec("INSERT INTO meal_plan (meal_plan_week_start_date) VALUES ('2026-11-02'), ('2026-11-09')");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // and once more: re-runnable

        assertEquals(2, count("SELECT COUNT(*) FROM meal_plan WHERE meal_plan_household_id = 3"),
                "both plans belong to the oldest household (the lowest id)");
        assertEquals(0, count("SELECT COUNT(*) FROM meal_plan WHERE meal_plan_household_id IS NULL"));
        // the second household can plan the same week, the first cannot plan it twice
        exec("INSERT INTO meal_plan (meal_plan_household_id, meal_plan_week_start_date) VALUES (5, '2026-11-02')");
        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO meal_plan (meal_plan_household_id, meal_plan_week_start_date) VALUES (3, '2026-11-02')"));
        // the foreign key holds
        assertThrows(SQLException.class, () -> exec(
                "INSERT INTO meal_plan (meal_plan_household_id, meal_plan_week_start_date) VALUES (99, '2026-12-07')"));
    }

    @Test
    void aDatabaseWithoutAnyHouseholdMigratesToo() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));

        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(0, count("SELECT COUNT(*) FROM meal_plan"));
    }

    @Test
    void migratedTableMatchesAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = describeMealPlan();

        TestDatabase.reset(List.of(OLD_TABLES, MIGRATION));
        List<String> migrated = describeMealPlan();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    // ---------- helpers ----------

    private static List<String> describeMealPlan() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('column ', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                    + "IS_NULLABLE) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'meal_plan' ORDER BY COLUMN_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('index ', INDEX_NAME, ' ', SEQ_IN_INDEX, ' ', COLUMN_NAME, "
                    + "' unique=', IF(NON_UNIQUE = 0, 'yes', 'no')) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan' ORDER BY INDEX_NAME, SEQ_IN_INDEX")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONCAT('constraint ', CONSTRAINT_NAME, ' ', CONSTRAINT_TYPE) "
                    + "FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'meal_plan' ORDER BY CONSTRAINT_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }
}
