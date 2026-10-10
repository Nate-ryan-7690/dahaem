package de.dahaem.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies the repair tool's expected output (scripts/importer/tests/fixtures/repair/
 * expected_repair_suspect.sql, which the Python test checks the tool still produces) to a
 * throwaway MariaDB holding the same state the tool read (state.json): the guarded SQL is valid
 * MariaDB, does what it says, runs inside one transaction, is a no-op the second time, and
 * leaves a row alone that changed after the tool read it.
 */
class RepairSqlTest {

    private static final Path REPAIR = Path.of("scripts", "importer", "tests", "fixtures", "repair");

    @BeforeEach
    void loadState() throws IOException, SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql"), Path.of("seed", "inventory_catalog.sql")));
        JsonNode state = new ObjectMapper().readTree(REPAIR.resolve("state.json").toFile());
        try (Connection con = Database.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO recipe (recipe_id, recipe_name, "
                    + "recipe_servings, recipe_cook_time, recipe_prep_time) VALUES (?, ?, ?, 30, 0)")) {
                for (JsonNode r : state.get("recipes")) {
                    ps.setInt(1, r.get("id").asInt());
                    ps.setString(2, r.get("name").asText());
                    ps.setInt(3, r.get("servings").asInt());
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, "
                    + "recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed, "
                    + "recipe_ingredient_to_taste) VALUES (?, ?, ?, ?, ?)")) {
                for (JsonNode r : state.get("ingredients")) {
                    ps.setInt(1, r.get("recipe_id").asInt());
                    ps.setInt(2, r.get("item_id").asInt());
                    ps.setString(3, r.get("unit").asText());
                    ps.setBigDecimal(4, r.get("quantity").isNull() ? null : new BigDecimal(r.get("quantity").asText()));
                    ps.setBoolean(5, r.get("to_taste").asBoolean());
                    ps.executeUpdate();
                }
            }
        }
    }

    @AfterAll
    static void close() throws IOException {
        TestDatabase.close();
    }

    @Test
    void repairSqlFixesTheKnownBugsAndNothingElse() throws Exception {
        int changed = applyRepairInOneTransaction();
        assertTrue(changed > 0);

        assertRow(1, 186, "0.500", "Stück", false);    // ½-bug: 5 -> 0.5
        assertRow(1, 40, "1.500", "Stück", false);     // rounding: 2 -> 1.5
        assertRow(1, 125, "0.250", "Bund", false);     // rounding: 1 -> 0.25
        assertRow(1, 1148, null, "", true);            // nach Geschmack Salz
        assertRow(1, 1022, "4.000", "EL", false);      // 3 EL + 1 EL inserted
        assertRow(1, 1070, "1.000", "TL", false);
        assertRow(1, 1156, null, "", true);            // Pfeffer, no amount: inserted to taste
        assertRow(1, 270, "250.000", "g", false);      // page says 300 g: held for review, untouched
        assertRow(1, 45, "1.000", "Stück", false);     // not on the page: left as is
        assertRow(1, 59, "5.000", "Stück", false);     // "½ Stück Gurke" is only an uncertain match: no update

        assertEquals(4, count("SELECT recipe_servings FROM recipe WHERE recipe_id = 2"));   // bundle 4P
        assertRow(2, 40, "0.667", "Stück", false);     // ⅔-bug: 667 -> 0.667
        assertRow(2, 1061, "2.000", "Prise", false);   // 2 Stück -> 2 Prise: the old parser's reading of "2 Prisen"
        assertRow(2, 1070, "1.000", "Stück", false);   // "1 TL Honig": unit change the old parser can't explain: held
        assertRow(2, 143, "1.000", "Stück", false);    // "Bratapfelgewürz" fuzzily matched to Apfel: held

        assertRow(7, 40, "0.500", "Stück", false);     // ½-bug fixed even though ...
        assertEquals(2, count("SELECT recipe_servings FROM recipe WHERE recipe_id = 7"));   // ... no recipeYield: kept

        assertEquals("https://www.hellofresh.de/recipes/zitronen-hahnchen-5f1a2b3c4d5e6f7a8b9c0d1e",
                string("SELECT recipe_source_url FROM recipe WHERE recipe_id = 1"));
        assertEquals(0, count("SELECT COUNT(*) FROM recipe WHERE recipe_id BETWEEN 3 AND 6 AND recipe_source_url IS NOT NULL"));
        assertRow(3, 40, "5.000", "Stück", false);     // ambiguous recipe: untouched
        assertRow(4, 186, "25.000", "Stück", false);   // unmatched recipes: untouched
        assertRow(5, 40, "75.000", "Stück", false);
    }

    @Test
    void secondRunChangesNothing() throws Exception {
        applyRepairInOneTransaction();
        String before = snapshot();
        assertEquals(0, applyRepairInOneTransaction());
        assertEquals(before, snapshot());
    }

    @Test
    void rowChangedAfterTheToolReadItIsLeftAlone() throws Exception {
        exec("UPDATE recipe_ingredient SET recipe_ingredient_quantity_needed = 3 "
                + "WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 186");
        exec("UPDATE recipe SET recipe_servings = 3 WHERE recipe_id = 2");
        applyRepairInOneTransaction();
        assertRow(1, 186, "3.000", "Stück", false);
        assertEquals(3, count("SELECT recipe_servings FROM recipe WHERE recipe_id = 2"));
        assertRow(1, 40, "1.500", "Stück", false);     // the rest still applies
    }

    // ---------- helpers ----------

    /** Runs every statement of the file inside one transaction, as the README tells Nate to; returns rows changed. */
    private static int applyRepairInOneTransaction() throws IOException, SQLException {
        List<String> statements = Files.readAllLines(REPAIR.resolve("expected_repair_suspect.sql"), StandardCharsets.UTF_8)
                .stream().filter(l -> !l.isBlank() && !l.startsWith("--")).toList();
        int changed = 0;
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                for (String statement : statements) {
                    changed += st.executeUpdate(statement);
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
        return changed;
    }

    private static void assertRow(int recipeId, int itemId, String quantity, String unit, boolean toTaste)
            throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT recipe_ingredient_quantity_needed, "
                     + "recipe_ingredient_unit, recipe_ingredient_to_taste FROM recipe_ingredient "
                     + "WHERE recipe_ingredient_recipe_id = ? AND recipe_ingredient_item_id = ?")) {
            ps.setInt(1, recipeId);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "no row for recipe " + recipeId + " item " + itemId);
                if (quantity == null) {
                    assertNull(rs.getBigDecimal(1));
                } else {
                    assertEquals(new BigDecimal(quantity), rs.getBigDecimal(1), "recipe " + recipeId + " item " + itemId);
                }
                assertEquals(unit, rs.getString(2));
                assertEquals(toTaste, rs.getBoolean(3));
            }
        }
    }

    private static String snapshot() throws SQLException {
        StringBuilder out = new StringBuilder();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT r.recipe_id, r.recipe_servings, r.recipe_source_url, "
                     + "ri.recipe_ingredient_item_id, ri.recipe_ingredient_unit, ri.recipe_ingredient_quantity_needed, "
                     + "ri.recipe_ingredient_to_taste FROM recipe r LEFT JOIN recipe_ingredient ri "
                     + "ON ri.recipe_ingredient_recipe_id = r.recipe_id ORDER BY 1, 4")) {
            while (rs.next()) {
                for (int i = 1; i <= 7; i++) {
                    out.append(rs.getString(i)).append(i == 7 ? "\n" : "|");
                }
            }
        }
        return out.toString();
    }

    private static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String string(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
