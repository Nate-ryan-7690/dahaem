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
 * migrations/0010_recipe_enrichment.sql on a throwaway MariaDB (Testcontainers), starting from the
 * recipe tables as they were before it (pre-0005 tables plus 0005 and 0006): it only adds nullable
 * columns, leaves every existing recipe untouched, can be run twice, and its CHECK allows only the
 * three image sources. That the migrated tables match a fresh schema.sql is checked in
 * RecipeQuantitiesMigrationTest.
 */
class RecipeEnrichmentMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0005_recipe_tables.sql");
    private static final List<Path> BEFORE = List.of(OLD_TABLES,
            Path.of("migrations", "0005_recipe_quantities.sql"),
            Path.of("migrations", "0006_recipe_servings_max.sql"));
    private static final Path MIGRATION = Path.of("migrations", "0010_recipe_enrichment.sql");

    @AfterAll
    static void close() throws IOException {
        TestDatabase.close();
    }

    @Test
    void addsNullableColumnsKeepsRowsAndIsReRunnable() throws SQLException {
        TestDatabase.reset(BEFORE);
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) "
                + "VALUES (1, 'Bolognese', 2, 30, 0), (2, 'Lasagne', 4, 60, 0)");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // once more: re-runnable

        assertEquals(2, count("SELECT COUNT(*) FROM recipe WHERE recipe_description IS NULL AND recipe_image_url IS NULL "
                + "AND recipe_thumbnail_url IS NULL AND recipe_image_source IS NULL AND recipe_cuisine IS NULL "
                + "AND recipe_rating_avg IS NULL AND recipe_rating_count IS NULL AND recipe_published_date IS NULL"));
        assertEquals(4, count("SELECT recipe_servings FROM recipe WHERE recipe_id = 2"));
        assertEquals(8, count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'recipe' AND IS_NULLABLE = 'YES' AND COLUMN_NAME IN ('recipe_description', "
                + "'recipe_image_url', 'recipe_thumbnail_url', 'recipe_image_source', 'recipe_cuisine', "
                + "'recipe_rating_avg', 'recipe_rating_count', 'recipe_published_date')"));
    }

    @Test
    void imageSourceCheckAllowsOnlyTheThreeValuesAndNull() throws SQLException {
        TestDatabase.reset(BEFORE);
        TestDatabase.apply(List.of(MIGRATION));
        assertSourceCheck();
    }

    @Test
    void freshSchemaHasTheSameCheckAndRatingPrecision() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        assertSourceCheck();
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, "
                + "recipe_rating_avg, recipe_rating_count, recipe_published_date) "
                + "VALUES (50, 'Bewertet', 2, 10, 0, 4.33, 120, '2024-03-05')");
        assertEquals(433, count("SELECT recipe_rating_avg * 100 FROM recipe WHERE recipe_id = 50"));
    }

    private static void assertSourceCheck() throws SQLException {
        for (String source : List.of("hellofresh", "own", "generated")) {
            exec("INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, "
                    + "recipe_image_source) VALUES ('" + source + "', 2, 10, 0, '" + source + "')");
        }
        exec("INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) "
                + "VALUES ('ohne Bild', 2, 10, 0)");
        assertThrows(SQLException.class, () -> exec("INSERT INTO recipe (recipe_name, recipe_servings, "
                + "recipe_cook_time, recipe_prep_time, recipe_image_source) VALUES ('falsch', 2, 10, 0, 'stock')"));
        assertThrows(SQLException.class, () -> exec("UPDATE recipe SET recipe_image_source = '' "
                + "WHERE recipe_name = 'own'"));
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
