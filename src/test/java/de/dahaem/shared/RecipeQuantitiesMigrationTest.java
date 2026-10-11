package de.dahaem.shared;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * migrations/0005_recipe_quantities.sql on a throwaway MariaDB (Testcontainers), starting from the
 * recipe tables as they were before it: it only widens and adds, keeps every existing value
 * exactly, can be run twice, and (with the later recipe migrations applied in order) leaves the same
 * columns a fresh schema.sql creates.
 */
class RecipeQuantitiesMigrationTest {

    private static final Path OLD_TABLES = Path.of("src", "test", "resources", "migrations", "pre_0005_recipe_tables.sql");
    private static final Path MIGRATION = Path.of("migrations", "0005_recipe_quantities.sql");
    // schema.sql also carries the later recipe migrations, so the comparison applies them in order too.
    private static final List<Path> LATER_MIGRATIONS = List.of(Path.of("migrations", "0006_recipe_servings_max.sql"));

    @AfterAll
    static void close() throws IOException {
        TestDatabase.close();
    }

    @Test
    void widensKeepsValuesAndIsReRunnable() throws SQLException {
        TestDatabase.reset(List.of(OLD_TABLES));
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_unit, "
                + "inventory_item_category) VALUES (1, 'lemon', 'Stück', 'fruit'), (2, 'beef', 'g', 'meat')");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) "
                + "VALUES (1, 'Zitronen-Hähnchen', 2, 30, 0), (2, 'Neu', 2, 30, 0), (3, 'Leer', 2, 30, 0)");
        exec("INSERT INTO recipe_ingredient VALUES (1, 1, 'Stück', 5), (2, 1, 'g', 2400)");

        TestDatabase.apply(List.of(MIGRATION));   // apply to the existing tables ...
        TestDatabase.apply(List.of(MIGRATION));   // ... and once more: re-runnable

        assertEquals(new BigDecimal("5.000"), quantity(1));
        assertEquals(new BigDecimal("2400.000"), quantity(2));
        assertEquals(0, count("SELECT COUNT(*) FROM recipe_ingredient WHERE recipe_ingredient_to_taste <> 0"));
        assertEquals(3, count("SELECT COUNT(*) FROM recipe WHERE recipe_source_url IS NULL"));

        exec("INSERT INTO recipe_ingredient VALUES (1, 2, 'Stück', 0.5, FALSE)");  // ½ Stück now fits
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, "
                + "recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) "
                + "VALUES (2, 2, '', NULL, TRUE)");
        assertEquals(new BigDecimal("0.500"), single("SELECT recipe_ingredient_quantity_needed FROM recipe_ingredient "
                + "WHERE recipe_ingredient_item_id = 1 AND recipe_ingredient_recipe_id = 2"));

        // the CHECK: a quantity, or to_taste — never neither, never both
        assertThrows(SQLException.class, () -> exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, "
                + "recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) "
                + "VALUES (1, 3, 'g', NULL)"));
        assertThrows(SQLException.class, () -> exec("INSERT INTO recipe_ingredient VALUES (2, 3, 'g', 10, TRUE)"));
    }

    @Test
    void migratedTablesMatchAFreshSchema() throws SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        List<String> fresh = recipeColumnsAndChecks();

        List<Path> migrations = new ArrayList<>(List.of(OLD_TABLES, MIGRATION));
        migrations.addAll(LATER_MIGRATIONS);
        TestDatabase.reset(migrations);
        List<String> migrated = recipeColumnsAndChecks();

        assertEquals(fresh, migrated);
        assertFalse(fresh.isEmpty());
    }

    // ---------- helpers ----------

    private static List<String> recipeColumnsAndChecks() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME IN ('recipe', 'recipe_ingredient') ORDER BY TABLE_NAME, COLUMN_NAME")) {
                while (rs.next()) {
                    out.add(rs.getString(1) + "." + rs.getString(2) + " " + rs.getString(3) + " null="
                            + rs.getString(4) + " default=" + rs.getString(5));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS "
                    + "WHERE CONSTRAINT_SCHEMA = DATABASE() ORDER BY CONSTRAINT_NAME")) {
                while (rs.next()) {
                    out.add("check " + rs.getString(1) + ": " + rs.getString(2));
                }
            }
        }
        return out;
    }

    private static BigDecimal quantity(int itemId) throws SQLException {
        return single("SELECT recipe_ingredient_quantity_needed FROM recipe_ingredient "
                + "WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = " + itemId);
    }

    private static BigDecimal single(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private static int count(String sql) throws SQLException {
        return single(sql).intValue();
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
