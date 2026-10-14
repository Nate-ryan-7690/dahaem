package de.dahaem.tags;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static de.dahaem.tags.TagFixtures.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * migrations/0011_recipe_tags_derived.sql on a throwaway MariaDB (Testcontainers): it only adds, existing
 * tag rows become 'manual', it can be run twice, and its CHECKs hold. The schema is built from schema.sql and
 * then taken back to how the live database looks before 0011.
 */
class RecipeTagsMigrationTest {

    private static final Path MIGRATION = Path.of("migrations", "0011_recipe_tags_derived.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    private static void backToBefore0011() {
        TestDatabase.reset();
        TagFixtures.run("DROP TABLE inventory_item_animal");
        // recipe_tag as it was in schema.sql before 0011
        TagFixtures.run("DROP TABLE recipe_tag");
        TagFixtures.run("CREATE TABLE recipe_tag (recipe_tag_recipe_id INTEGER, recipe_tag_id INTEGER, "
                + "PRIMARY KEY(recipe_tag_recipe_id, recipe_tag_id), "
                + "FOREIGN KEY(recipe_tag_recipe_id) REFERENCES recipe(recipe_id), "
                + "FOREIGN KEY(recipe_tag_id) REFERENCES tag(tag_id))");
    }

    @Test
    void addsTheTableAndColumnKeepsExistingTagsAsManualAndIsReRunnable() {
        backToBefore0011();
        int recipe = TagFixtures.recipe("Altes Rezept", 10, 10, null, null);
        TagFixtures.run("INSERT INTO tag (tag_name, tag_category) VALUES ('Lieblingsessen', 'Eigene')");
        TagFixtures.run("INSERT INTO recipe_tag (recipe_tag_recipe_id, recipe_tag_id) "
                + "SELECT ?, tag_id FROM tag", recipe);

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // once more: re-runnable

        assertEquals(1, count("SELECT COUNT(*) FROM recipe_tag WHERE recipe_tag_source = 'manual'"));
        assertEquals(1, count("SELECT COUNT(*) FROM recipe"));
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"), "the table starts empty");
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'recipe_tag' AND INDEX_NAME = 'recipe_tag_by_tag' AND SEQ_IN_INDEX = 1 "
                + "AND COLUMN_NAME = 'recipe_tag_id'"));
    }

    @Test
    void checksRejectAnythingOutsideTheTierListAndTheTwoSources() {
        backToBefore0011();
        TestDatabase.apply(List.of(MIGRATION));
        checks();
    }

    @Test
    void freshSchemaHasTheSameChecks() {
        TestDatabase.reset();
        checks();
    }

    private static void checks() {
        int item = TagFixtures.item("gemischtes hackfleisch", "meat", "fresh meat");
        TagFixtures.animal(item, "Rind");
        TagFixtures.animal(item, "Schwein");
        assertThrows(RuntimeException.class, () -> TagFixtures.animal(item, "Einhorn"));
        assertThrows(RuntimeException.class, () -> TagFixtures.animal(item, "Rind"), "primary key: both columns");
        assertThrows(RuntimeException.class, () -> TagFixtures.animal(999_999, "Rind"), "foreign key to the item");

        int recipe = TagFixtures.recipe("Test", 1, 1, null, null);
        TagFixtures.tag(recipe, "a", "x", "derived");
        assertThrows(RuntimeException.class, () -> TagFixtures.tag(recipe, "b", "x", "robot"));
        TagFixtures.run("INSERT INTO recipe_tag (recipe_tag_recipe_id, recipe_tag_id) "
                + "SELECT ?, tag_id FROM tag WHERE tag_name = 'b'", recipe);
        assertEquals("manual", sourceOf(recipe, "b"), "the default is manual");
    }

    private static String sourceOf(int recipe, String tag) {
        return TagFixtures.tags(recipe, "manual").contains(tag) ? "manual" : "other";
    }
}
