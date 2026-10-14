package de.dahaem.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies the rematch tool's expected output (scripts/importer/tests/fixtures/rematch/expected_rematch.sql, which the
 * Python test checks the tool still produces) to a throwaway MariaDB holding the made-up state the tool read
 * (state.json): the guarded SQL is valid MariaDB, inserts the new ingredient rows and deletes the resolved unmatched
 * rows inside one transaction, leaves review / no-item / unchecked rows alone, is a no-op the second time, and does
 * not delete a covered row whose covering ingredient has disappeared.
 */
class RematchSqlTest {

    private static final Path FIXTURES = Path.of("scripts", "importer", "tests", "fixtures", "rematch");

    @BeforeEach
    void loadState() throws IOException, SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        JsonNode state = new ObjectMapper().readTree(FIXTURES.resolve("state.json").toFile());
        try (Connection con = Database.getConnection()) {
            for (JsonNode i : state.get("items")) {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO inventory_item (inventory_item_id, "
                        + "inventory_item_name_english, inventory_item_name_deutsch, inventory_item_unit, "
                        + "inventory_item_category) VALUES (?, ?, ?, 'g', 'test')")) {
                    ps.setInt(1, i.get(0).asInt());
                    ps.setString(2, i.get(1).asText());
                    ps.setString(3, i.get(2).asText());
                    ps.executeUpdate();
                }
            }
            for (JsonNode r : state.get("recipes")) {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO recipe (recipe_id, recipe_name, "
                        + "recipe_servings, recipe_cook_time, recipe_prep_time, recipe_source_url) "
                        + "VALUES (?, ?, 2, 30, 0, ?)")) {
                    ps.setInt(1, r.get("id").asInt());
                    ps.setString(2, r.get("name").asText());
                    ps.setString(3, r.get("source_url").isNull() ? null : r.get("source_url").asText());
                    ps.executeUpdate();
                }
            }
            for (JsonNode g : state.get("ingredients")) {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO recipe_ingredient (recipe_ingredient_"
                        + "recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, "
                        + "recipe_ingredient_quantity_needed) VALUES (?, ?, 'g', 100)")) {
                    ps.setInt(1, g.get("recipe_id").asInt());
                    ps.setInt(2, g.get("item_id").asInt());
                    ps.executeUpdate();
                }
            }
            for (JsonNode u : state.get("unmatched")) {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO recipe_unmatched_ingredient "
                        + "(recipe_unmatched_ingredient_id, recipe_unmatched_ingredient_recipe_id, "
                        + "recipe_unmatched_ingredient_line_no, recipe_unmatched_ingredient_raw_text, "
                        + "recipe_unmatched_ingredient_reason) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setInt(1, u.get("id").asInt());
                    ps.setInt(2, u.get("recipe_id").asInt());
                    ps.setObject(3, u.get("line_no").isNull() ? null : u.get("line_no").asInt());
                    ps.setString(4, u.get("raw_text").asText());
                    ps.setString(5, u.get("reason").asText());
                    ps.executeUpdate();
                }
            }
        }
    }

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    private static Path script(boolean commit) throws IOException {
        Path file = Files.createTempFile("rematch", ".sql");
        file.toFile().deleteOnExit();
        Files.writeString(file, "SET NAMES utf8mb4; START TRANSACTION;\n"
                + Files.readString(FIXTURES.resolve("expected_rematch.sql"), StandardCharsets.UTF_8)
                + (commit ? "COMMIT;\n" : "ROLLBACK;\n"), StandardCharsets.UTF_8);
        return file;
    }

    private static List<String> query(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int c = 1; c <= cols; c++) {
                    sb.append(c > 1 ? "|" : "").append(rs.getString(c));
                }
                out.add(sb.toString());
            }
        }
        return out;
    }

    private static List<String> ingredients() throws SQLException {
        return query("SELECT recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, "
                + "recipe_ingredient_quantity_needed, recipe_ingredient_to_taste FROM recipe_ingredient "
                + "ORDER BY recipe_ingredient_recipe_id, recipe_ingredient_item_id");
    }

    private static List<String> unmatched() throws SQLException {
        return query("SELECT recipe_unmatched_ingredient_id FROM recipe_unmatched_ingredient "
                + "ORDER BY recipe_unmatched_ingredient_id");
    }

    @Test
    void theSqlInsertsTheRowsDeletesTheResolvedOnesAndAppliesTwiceWithoutChange() throws Exception {
        assertEquals(34, unmatched().size());
        int before = ingredients().size();
        TestDatabase.apply(List.of(script(true)));
        List<String> ing = ingredients();
        assertEquals(before + 12, ing.size());
        assertTrue(ing.contains("1|1028|EL|2.000|0"), "Sonnenblumenöl 2 EL");
        assertTrue(ing.contains("1|1156||null|1"), "Pfeffer to taste");
        assertTrue(ing.contains("2|1070|g|150.000|0"), "two Honig lines summed");
        assertTrue(ing.contains("5|1050||null|1"), "Weißweinessig to taste");
        assertTrue(ing.contains("15|1070|EL|1.000|0"), "from the steps");
        assertTrue(ing.contains("18|1061|g|100.000|0"), "amount beats to taste");
        assertTrue(ing.stream().noneMatch(s -> s.startsWith("3|")), "different units: nothing inserted");
        assertEquals(34 - 24, unmatched().size());

        List<String> after = ingredients();
        List<String> remaining = unmatched();
        TestDatabase.apply(List.of(script(true)));
        assertEquals(after, ingredients(), "guarded: a second run inserts nothing");
        assertEquals(remaining, unmatched(), "guarded: a second run deletes nothing");
    }

    @Test
    void reviewNoItemAndUncheckedRowsStayAndNothingIsKeptOnRollback() throws Exception {
        TestDatabase.apply(List.of(script(true)));
        List<String> left = query("SELECT recipe_unmatched_ingredient_recipe_id, recipe_unmatched_ingredient_raw_text, "
                + "recipe_unmatched_ingredient_reason FROM recipe_unmatched_ingredient "
                + "ORDER BY recipe_unmatched_ingredient_id");
        assertEquals(10, left.size());
        assertTrue(left.contains("3|100 g Honig|unmatched"));
        assertTrue(left.contains("7|4 Stück Schaschlikspieße|unmatched"));
        assertTrue(left.contains("8|Zauberpulver XY|unmatched"));
        assertTrue(left.contains("12|300 g Kartoffel|unmatched"));
        assertTrue(left.contains("13|5 g Honig|unmatched"));
        assertTrue(left.contains("14|100 g Honig|unmatched"));
        assertTrue(left.contains("10|(keine Quell-URL)|unchecked"));
        assertTrue(left.contains("11|(Seite nicht im Cache)|unchecked"));
    }

    @Test
    void aRolledBackRunKeepsNothing() throws Exception {
        int ingredientsBefore = ingredients().size();
        TestDatabase.apply(List.of(script(false)));
        assertEquals(ingredientsBefore, ingredients().size());
        assertEquals(34, unmatched().size());
    }

    @Test
    void aCoveredRowIsKeptWhenItsCoveringIngredientIsGone() throws Exception {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM recipe_ingredient "
                     + "WHERE recipe_ingredient_recipe_id = 1 AND recipe_ingredient_item_id = 1023")) {
            ps.executeUpdate();   // throwaway database
        }
        TestDatabase.apply(List.of(script(true)));
        List<String> left = query("SELECT recipe_unmatched_ingredient_raw_text FROM recipe_unmatched_ingredient "
                + "WHERE recipe_unmatched_ingredient_recipe_id = 1");
        assertEquals(List.of("Öl", "(Oliven-)Öl*"), left, "no olive oil any more: the family cover no longer holds");
    }

    @Test
    void aRecipeThatNoLongerExistsIsSkippedNotAnError() throws Exception {
        try (Connection con = Database.getConnection()) {
            for (String sql : List.of("DELETE FROM recipe_unmatched_ingredient WHERE recipe_unmatched_ingredient_recipe_id = 9",
                    "DELETE FROM recipe WHERE recipe_id = 9")) {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.executeUpdate();   // throwaway database
                }
            }
        }
        int before = ingredients().size();
        TestDatabase.apply(List.of(script(true)));
        assertEquals(before + 9, ingredients().size(), "the three Fleisch rows are not inserted");
    }
}
