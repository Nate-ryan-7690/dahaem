package de.dahaem.tags;

import de.dahaem.shared.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the tags slice against a real, temporary SQLite file built from the project's
 * actual schema.sql. The repository under test is never mocked: only Database's target file
 * is swapped out.
 */
class TagServiceTest {

    private Path dbFile;
    private TagRepository repository;
    private TagService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        dbFile = Files.createTempFile("dahaem-tags-test", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty("dahaem.db.path", dbFile.toAbsolutePath().toString());
        createSchemaFromFile();
        repository = new TagRepository();
        service = new TagService(repository);
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("dahaem.db.path");
        Files.deleteIfExists(dbFile);
    }

    @Test
    void findOrCreateTagCreatesOnceAndDedupsOnName() {
        Tag first = repository.findOrCreateTag("Vegetarian", "diet");
        Tag second = repository.findOrCreateTag("Vegetarian", "diet");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, repository.allTags().size());
    }

    @Test
    void addTagToRecipeLinksTheTagAndIsSafeToRepeat() throws SQLException {
        int recipeId = insertRecipe("Pfannkuchen");

        service.addTagToRecipe(recipeId, "Quick", "time");
        service.addTagToRecipe(recipeId, "Quick", "time");

        List<Tag> tags = repository.tagsForRecipe(recipeId);
        assertEquals(1, tags.size());
        assertEquals("Quick", tags.get(0).getName());
        assertEquals("time", tags.get(0).getCategory());
    }

    @Test
    void tagsAreQueryableInBothDirections() throws SQLException {
        int suppeId = insertRecipe("Suppe");
        int eintopfId = insertRecipe("Eintopf");

        Tag vegan = service.addTagToRecipe(suppeId, "Vegan", "diet");
        service.addTagToRecipe(eintopfId, "Vegan", "diet");
        service.addTagToRecipe(suppeId, "Quick", "time");

        List<Tag> suppeTags = service.tagsForRecipe(suppeId);
        assertEquals(2, suppeTags.size());
        assertTrue(suppeTags.stream().anyMatch(t -> t.getName().equals("Vegan")));
        assertTrue(suppeTags.stream().anyMatch(t -> t.getName().equals("Quick")));

        List<Integer> veganRecipeIds = repository.recipesForTag(vegan.getId());
        assertEquals(2, veganRecipeIds.size());
        assertTrue(veganRecipeIds.containsAll(List.of(suppeId, eintopfId)));

        List<Integer> viaService = service.recipesWithTag("Vegan");
        assertEquals(veganRecipeIds, viaService);
        assertTrue(service.recipesWithTag("Nonexistent").isEmpty());
    }

    private int insertRecipe(String name) throws SQLException {
        String sql = "INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) " +
                "VALUES (?, 2, 20, 10)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void createSchemaFromFile() throws IOException, SQLException {
        String schema = Files.readString(Path.of("schema.sql"));
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            for (String rawStatement : schema.split(";")) {
                String statement = rawStatement.replaceAll("--.*", "").trim();
                if (!statement.isEmpty()) {
                    st.execute(statement);
                }
            }
        }
    }
}
