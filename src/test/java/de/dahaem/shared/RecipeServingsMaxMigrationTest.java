package de.dahaem.shared;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * migrations/0006_recipe_servings_max.sql on a throwaway MariaDB (Testcontainers), starting from the
 * recipe tables as they were before it (pre-0005 tables plus 0005): it only adds, leaves every
 * existing recipe as "not a range", can be run twice, and its CHECK keeps servings_max above servings.
 * That the migrated tables match a fresh schema.sql is checked in RecipeQuantitiesMigrationTest.
 */
class RecipeServingsMaxMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0005_recipe_tables.sql");
    private static final Path MIGRATION_0005 = Path.of("migrations", "0005_recipe_quantities.sql");
    private static final Path MIGRATION = Path.of("migrations", "0006_recipe_servings_max.sql");

    @AfterAll
    static void close() throws IOException {
        TestDatabase.close();
    }

    @Test
    void addsNullableColumnKeepsRowsAndIsReRunnable() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES, MIGRATION_0005));
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) "
                + "VALUES (1, 'Bolognese', 2, 30, 0), (2, 'Familien-Lasagne', 4, 60, 0)");

        TestDatabase.apply(List.of(MIGRATION));   // apply to the existing table ...
        TestDatabase.apply(List.of(MIGRATION));   // ... and once more: re-runnable

        assertEquals(2, count("SELECT COUNT(*) FROM recipe WHERE recipe_servings_max IS NULL"));
        assertEquals(2, count("SELECT recipe_servings FROM recipe WHERE recipe_id = 1"));
        assertEquals(4, count("SELECT recipe_servings FROM recipe WHERE recipe_id = 2"));
    }

    @Test
    void checkRejectsMaxNotAboveServings() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES, MIGRATION_0005, MIGRATION));
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_servings_max, recipe_cook_time, "
                + "recipe_prep_time) VALUES (1, '3 – 4 Personen', 3, 4, 30, 0), (2, '2 – 4 Personen', 2, 4, 30, 0), "
                + "(3, 'für 4 Personen', 4, NULL, 30, 0)");
        assertEquals(4, count("SELECT recipe_servings_max FROM recipe WHERE recipe_id = 1"));

        assertThrows(SQLException.class, () -> exec("INSERT INTO recipe (recipe_name, recipe_servings, "
                + "recipe_servings_max, recipe_cook_time, recipe_prep_time) VALUES ('gleich', 3, 3, 30, 0)"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO recipe (recipe_name, recipe_servings, "
                + "recipe_servings_max, recipe_cook_time, recipe_prep_time) VALUES ('kleiner', 4, 3, 30, 0)"));
        assertThrows(SQLException.class, () -> exec("UPDATE recipe SET recipe_servings = 4 WHERE recipe_id = 1"));
    }

    @Test
    void freshSchemaHasTheSameCheck() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        exec("INSERT INTO recipe (recipe_name, recipe_servings, recipe_servings_max, recipe_cook_time, "
                + "recipe_prep_time) VALUES ('3 – 4 Personen', 3, 4, 30, 0)");
        assertThrows(SQLException.class, () -> exec("INSERT INTO recipe (recipe_name, recipe_servings, "
                + "recipe_servings_max, recipe_cook_time, recipe_prep_time) VALUES ('gleich', 2, 2, 30, 0)"));
    }

    // ---------- helpers ----------

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
