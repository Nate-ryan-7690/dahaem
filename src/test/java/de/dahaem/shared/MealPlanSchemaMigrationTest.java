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
 * migrations/0016_meal_plan_entries.sql on a throwaway MariaDB (Testcontainers), starting from household, recipe,
 * meal_plan and meal_plan_recipe as they were before it: existing rows are kept and numbered, the same recipe can be
 * planned many times, the old three-column insert still works, the primary key is the new entry id while both foreign
 * keys stay, the new checks hold, it can be run twice, and the migrated tables match a fresh schema.sql.
 */
class MealPlanSchemaMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0016_meal_plan_tables.sql");
    private static final Path MIGRATION = Path.of("migrations", "0016_meal_plan_entries.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    private static void oldDataBase() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO household (household_name, created_date) VALUES ('Zuhause', '2026-11-04')");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) "
                + "VALUES (1, 'Suppe', 2, 20, 0), (2, 'Auflauf', 2, 40, 0)");
        exec("INSERT INTO meal_plan (meal_plan_id, meal_plan_week_start_date) VALUES (1, '2026-11-02')");
    }

    @Test
    void keepsExistingRowsNumbersThemAndIsReRunnable() throws SQLException {
        oldDataBase();
        exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, meal_plan_recipe_meal_time) "
                + "VALUES (1, 1, 'Abendessen'), (2, 1, 'Mittagessen')");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // and once more: re-runnable

        assertEquals(2, count("SELECT COUNT(*) FROM meal_plan_recipe"));
        assertEquals(2, count("SELECT COUNT(DISTINCT meal_plan_recipe_entry_id) FROM meal_plan_recipe"));
        assertEquals(1, count("SELECT COUNT(*) FROM meal_plan_recipe WHERE meal_plan_recipe_id = 1 "
                + "AND meal_plan_recipe_plan_id = 1 AND meal_plan_recipe_meal_time = 'Abendessen' "
                + "AND meal_plan_recipe_date IS NULL AND meal_plan_recipe_servings IS NULL "
                + "AND meal_plan_recipe_position = 0 AND meal_plan_recipe_note IS NULL"));
        assertEquals(1, count("SELECT COUNT(*) FROM household WHERE household_people IS NULL"));
        assertEquals(2, count("SELECT COUNT(*) FROM recipe WHERE recipe_preferred_servings IS NULL"));
    }

    @Test
    void theSameRecipeCanBePlannedManyTimesAndTheOldInsertStillWorks() throws SQLException {
        oldDataBase();
        TestDatabase.apply(List.of(MIGRATION));

        // the old endpoint's insert, twice for the same recipe and plan: used to be a duplicate-key error
        exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, meal_plan_recipe_meal_time) "
                + "VALUES (1, 1, 'Mittagessen')");
        exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, meal_plan_recipe_meal_time) "
                + "VALUES (1, 1, 'Abendessen')");
        // a new-style meal with a day, servings, an order and a note
        exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, meal_plan_recipe_date, "
                + "meal_plan_recipe_meal_time, meal_plan_recipe_servings, meal_plan_recipe_position, meal_plan_recipe_note) "
                + "VALUES (1, 1, '2026-11-02', 'Snack', 3, 1, 'Reste')");

        assertEquals(3, count("SELECT COUNT(*) FROM meal_plan_recipe WHERE meal_plan_recipe_id = 1 "
                + "AND meal_plan_recipe_plan_id = 1"));
        assertEquals(3, count("SELECT COUNT(DISTINCT meal_plan_recipe_entry_id) FROM meal_plan_recipe"));
        // the old endpoint's delete by (plan, recipe) still works (it removes every entry of that recipe in the plan)
        assertEquals(3, update("DELETE FROM meal_plan_recipe WHERE meal_plan_recipe_plan_id = 1 AND meal_plan_recipe_id = 1"));
    }

    @Test
    void primaryKeyIsTheEntryIdAndBothForeignKeysRemain() throws SQLException {
        oldDataBase();
        TestDatabase.apply(List.of(MIGRATION));

        assertEquals(List.of("meal_plan_recipe_entry_id"), strings("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_recipe' AND CONSTRAINT_NAME = 'PRIMARY'"));
        assertEquals(List.of("meal_plan_recipe_id -> recipe", "meal_plan_recipe_plan_id -> meal_plan"),
                strings("SELECT CONCAT(COLUMN_NAME, ' -> ', REFERENCED_TABLE_NAME) FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_recipe' "
                        + "AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY COLUMN_NAME"));
        // the foreign keys are enforced: an unknown recipe or plan is refused
        assertThrows(SQLException.class, () -> exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, "
                + "meal_plan_recipe_plan_id) VALUES (999, 1)"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, "
                + "meal_plan_recipe_plan_id) VALUES (1, 999)"));
    }

    @Test
    void checksRefuseZeroPeopleAndAcceptNullAndOne() throws SQLException {
        oldDataBase();
        TestDatabase.apply(List.of(MIGRATION));

        assertThrows(SQLException.class, () -> exec("UPDATE household SET household_people = 0"));
        assertThrows(SQLException.class, () -> exec("UPDATE recipe SET recipe_preferred_servings = 0 WHERE recipe_id = 1"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, "
                + "meal_plan_recipe_plan_id, meal_plan_recipe_servings) VALUES (1, 1, 0)"));

        exec("UPDATE household SET household_people = 1");
        exec("UPDATE household SET household_people = NULL");
        exec("UPDATE recipe SET recipe_preferred_servings = 1 WHERE recipe_id = 1");
        exec("UPDATE recipe SET recipe_preferred_servings = 4 WHERE recipe_id = 2");   // a preference may be above or below the base
        exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, meal_plan_recipe_servings) "
                + "VALUES (1, 1, 1)");
    }

    @Test
    void migratedTablesMatchAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = columnsAndChecks();

        // schema.sql also has the later 0022 column, so that migration is applied on top here
        TestDatabase.reset(List.of(OLD_TABLES, MIGRATION, Path.of("migrations", "0022_meal_plan_household.sql")));
        List<String> migrated = columnsAndChecks();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    // ---------- helpers ----------

    private static List<String> columnsAndChecks() throws SQLException {
        String tables = "('household', 'recipe', 'meal_plan', 'meal_plan_recipe')";
        List<String> out = new ArrayList<>(strings("SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', "
                + "IS_NULLABLE, ' default=', IFNULL(COLUMN_DEFAULT, 'NULL')) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN " + tables + " ORDER BY TABLE_NAME, COLUMN_NAME"));
        out.addAll(strings("SELECT CONCAT('check ', CONSTRAINT_NAME, ': ', CHECK_CLAUSE) FROM information_schema.CHECK_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME IN " + tables + " ORDER BY CONSTRAINT_NAME"));
        return out;
    }

    private static List<String> strings(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
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

    private static int update(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            return st.executeUpdate(sql);
        }
    }
}
