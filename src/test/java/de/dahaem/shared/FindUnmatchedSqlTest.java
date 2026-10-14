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

/**
 * Applies the unmatched-lines tool's expected output (scripts/importer/tests/fixtures/unmatched/
 * expected_unmatched.sql, which the Python test checks the tool still produces) to a throwaway MariaDB holding the
 * recipes the tool read (state.json): the guarded SQL is valid MariaDB, stores each row inside one transaction, is a
 * no-op the second time, and skips a recipe that no longer exists instead of failing.
 */
class FindUnmatchedSqlTest {

    private static final Path FIXTURES = Path.of("scripts", "importer", "tests", "fixtures", "unmatched");

    @BeforeEach
    void loadState() throws IOException, SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        JsonNode state = new ObjectMapper().readTree(FIXTURES.resolve("state.json").toFile());
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, "
                     + "recipe_cook_time, recipe_prep_time, recipe_source_url) VALUES (?, ?, 2, 30, 0, ?)")) {
            for (JsonNode r : state.get("recipes")) {
                ps.setInt(1, r.get("id").asInt());
                ps.setString(2, r.get("name").asText());
                ps.setString(3, r.get("source_url").isNull() ? null : r.get("source_url").asText());
                ps.executeUpdate();
            }
        }
    }

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    private static String script() throws IOException {
        return "SET NAMES utf8mb4; START TRANSACTION;\n"
                + Files.readString(FIXTURES.resolve("expected_unmatched.sql"), StandardCharsets.UTF_8) + "COMMIT;\n";
    }

    private static List<String> rows() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT recipe_unmatched_ingredient_recipe_id, "
                     + "recipe_unmatched_ingredient_line_no, recipe_unmatched_ingredient_raw_text, "
                     + "recipe_unmatched_ingredient_reason FROM recipe_unmatched_ingredient "
                     + "ORDER BY recipe_unmatched_ingredient_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getInt(1) + "|" + rs.getObject(2) + "|" + rs.getString(3) + "|" + rs.getString(4));
            }
        }
        return out;
    }

    @Test
    void theSqlStoresEveryRowAndAppliesTwiceWithoutDuplicates() throws Exception {
        TestDatabase.apply(List.of(writeTemp()));
        List<String> first = rows();
        assertEquals(9, first.size());
        assertEquals("1|3|50 g Speck|unmatched", first.get(0));
        assertEquals("3|null|(keine Quell-URL)|unchecked", first.get(1));
        assertEquals("7|3|100 g Mamas 'Spezial' Soße|unmatched", first.get(5));
        assertEquals("8|3|100 g Speck|unmatched", first.get(8));

        TestDatabase.apply(List.of(writeTemp()));
        assertEquals(first, rows(), "guarded: a second run adds nothing");
    }

    @Test
    void aRecipeThatNoLongerExistsIsSkippedNotAnError() throws Exception {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM recipe WHERE recipe_id = 3")) {
            ps.executeUpdate();   // throwaway database
        }
        TestDatabase.apply(List.of(writeTemp()));
        assertEquals(8, rows().size());
    }

    private static Path writeTemp() throws IOException {
        Path file = Files.createTempFile("unmatched", ".sql");
        file.toFile().deleteOnExit();
        Files.writeString(file, script(), StandardCharsets.UTF_8);
        return file;
    }
}
