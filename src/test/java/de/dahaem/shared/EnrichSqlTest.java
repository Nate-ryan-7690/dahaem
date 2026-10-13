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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Applies the enrichment tool's expected output (scripts/importer/tests/fixtures/enrich/
 * expected_enrich.sql, which the Python test checks the tool still produces) to a throwaway MariaDB
 * holding the state the tool read (state.json): the guarded SQL is valid MariaDB, fills what it says
 * inside one transaction, is a no-op the second time and never overwrites a value set after the tool read.
 */
class EnrichSqlTest {

    private static final Path ENRICH = Path.of("scripts", "importer", "tests", "fixtures", "enrich");

    @BeforeEach
    void loadState() throws IOException, SQLException {
        TestDatabase.reset(List.of(Path.of("schema.sql")));
        JsonNode state = new ObjectMapper().readTree(ENRICH.resolve("state.json").toFile());
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, "
                     + "recipe_cook_time, recipe_prep_time, recipe_source_url, recipe_description, recipe_image_url, "
                     + "recipe_image_source, recipe_cuisine) VALUES (?, ?, 2, 30, 0, ?, ?, ?, ?, ?)")) {
            for (JsonNode r : state.get("recipes")) {
                ps.setInt(1, r.get("id").asInt());
                ps.setString(2, "Rezept " + r.get("id").asInt());
                ps.setString(3, text(r, "source_url"));
                ps.setString(4, text(r, "description"));
                ps.setString(5, text(r, "image_url"));
                ps.setString(6, text(r, "image_source"));
                ps.setString(7, text(r, "cuisine"));
                ps.executeUpdate();
            }
        }
    }

    @AfterAll
    static void close() throws IOException {
        TestDatabase.close();
    }

    @Test
    void fillsOnlyEmptyFieldsAndKeepsTheOwnImage() throws Exception {
        assertEquals(4, applyInOneTransaction());

        // recipe 1: everything filled, with the quote and backslash of the text intact
        assertEquals("Ein erfundener Text mit 'Anführungszeichen' und \\ Backslash.",
                string("SELECT recipe_description FROM recipe WHERE recipe_id = 1"));
        assertEquals("https://img.example.test/full-0001.jpg", string("SELECT recipe_image_url FROM recipe WHERE recipe_id = 1"));
        assertEquals("https://img.example.test/thumb/full-0001.jpg",
                string("SELECT recipe_thumbnail_url FROM recipe WHERE recipe_id = 1"));
        assertEquals("hellofresh", string("SELECT recipe_image_source FROM recipe WHERE recipe_id = 1"));
        assertEquals("Schwedisch", string("SELECT recipe_cuisine FROM recipe WHERE recipe_id = 1"));
        assertEquals("4.33", string("SELECT recipe_rating_avg FROM recipe WHERE recipe_id = 1"));
        assertEquals("120", string("SELECT recipe_rating_count FROM recipe WHERE recipe_id = 1"));
        assertEquals("2024-03-05", string("SELECT recipe_published_date FROM recipe WHERE recipe_id = 1"));

        // recipe 2: image only; recipe 3: description only, cuisine stays NULL (not in the map)
        assertEquals("hellofresh", string("SELECT recipe_image_source FROM recipe WHERE recipe_id = 2"));
        assertNull(string("SELECT recipe_cuisine FROM recipe WHERE recipe_id = 2"));
        assertEquals("Nur ein Satz.", string("SELECT recipe_description FROM recipe WHERE recipe_id = 3"));
        assertNull(string("SELECT recipe_cuisine FROM recipe WHERE recipe_id = 3"));
        assertNull(string("SELECT recipe_image_source FROM recipe WHERE recipe_id = 3"));

        // recipe 4: own image and hand-written text stay; the empty fields are filled
        assertEquals("own", string("SELECT recipe_image_source FROM recipe WHERE recipe_id = 4"));
        assertEquals("https://own.example.test/photo-4.jpg", string("SELECT recipe_image_url FROM recipe WHERE recipe_id = 4"));
        assertNull(string("SELECT recipe_thumbnail_url FROM recipe WHERE recipe_id = 4"));
        assertEquals("Von Hand geschrieben", string("SELECT recipe_description FROM recipe WHERE recipe_id = 4"));
        assertEquals("Amerikanisch", string("SELECT recipe_cuisine FROM recipe WHERE recipe_id = 4"));
        assertEquals("5.00", string("SELECT recipe_rating_avg FROM recipe WHERE recipe_id = 4"));

        // recipes without a page or without a URL: untouched
        assertEquals(0, count("SELECT COUNT(*) FROM recipe WHERE recipe_id IN (5, 6, 7) AND (recipe_image_url IS NOT NULL "
                + "OR recipe_rating_avg IS NOT NULL OR recipe_published_date IS NOT NULL)"));
    }

    @Test
    void secondRunChangesNothing() throws Exception {
        applyInOneTransaction();
        String before = snapshot();
        assertEquals(0, applyInOneTransaction());
        assertEquals(before, snapshot());
    }

    @Test
    void valuesSetAfterTheToolReadItAreNeverOverwritten() throws Exception {
        exec("UPDATE recipe SET recipe_image_url = 'https://own.example.test/late.jpg', recipe_image_source = 'generated', "
                + "recipe_cuisine = 'Fusion', recipe_description = 'später gesetzt' WHERE recipe_id = 1");
        applyInOneTransaction();
        assertEquals("https://own.example.test/late.jpg", string("SELECT recipe_image_url FROM recipe WHERE recipe_id = 1"));
        assertEquals("generated", string("SELECT recipe_image_source FROM recipe WHERE recipe_id = 1"));
        assertNull(string("SELECT recipe_thumbnail_url FROM recipe WHERE recipe_id = 1"));   // no linked thumb next to a generated image
        assertEquals("Fusion", string("SELECT recipe_cuisine FROM recipe WHERE recipe_id = 1"));
        assertEquals("später gesetzt", string("SELECT recipe_description FROM recipe WHERE recipe_id = 1"));
        assertEquals("4.33", string("SELECT recipe_rating_avg FROM recipe WHERE recipe_id = 1"));   // the rest still applies
    }

    // ---------- helpers ----------

    /** Runs the expected file the way the llmbox session does: START TRANSACTION, the file, COMMIT. Returns rows changed. */
    private static int applyInOneTransaction() throws IOException, SQLException {
        List<String> statements = new ArrayList<>();
        for (String line : Files.readAllLines(ENRICH.resolve("expected_enrich.sql"), StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !line.startsWith("--")) {
                statements.add(line);
            }
        }
        int changed = 0;
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                for (String sql : statements) {
                    changed += st.executeUpdate(sql);
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
        return changed;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static String snapshot() throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT recipe_id, recipe_description, recipe_image_url, recipe_thumbnail_url, "
                     + "recipe_image_source, recipe_cuisine, recipe_rating_avg, recipe_rating_count, recipe_published_date "
                     + "FROM recipe ORDER BY recipe_id")) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= columns; i++) {
                    sb.append(rs.getString(i)).append('|');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static int count(String sql) throws SQLException {
        return Integer.parseInt(string(sql));
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
