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
 * migrations/0012_diet_filter.sql on a throwaway MariaDB (Testcontainers): it only adds, replacing the CHECK by a
 * wider one changes no data, existing animals become 'certain', it can be run twice, and its CHECKs hold. The schema
 * is built from schema.sql and then taken back to how the live database looks after 0011.
 */
class DietFilterMigrationTest {

    private static final Path MIGRATION = Path.of("migrations", "0012_diet_filter.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    private static void backToAfter0011() {
        TestDatabase.reset();
        TagFixtures.run("DROP TABLE recipe_unmatched_ingredient");
        TagFixtures.run("ALTER TABLE inventory_item DROP COLUMN inventory_item_alcohol");
        TagFixtures.run("DROP TABLE inventory_item_animal");
        TagFixtures.run("CREATE TABLE inventory_item_animal ("
                + "inventory_item_animal_item_id INTEGER NOT NULL, inventory_item_animal_animal VARCHAR(20) NOT NULL, "
                + "PRIMARY KEY (inventory_item_animal_item_id, inventory_item_animal_animal), "
                + "CONSTRAINT inventory_item_animal_is_tier CHECK (inventory_item_animal_animal IN "
                + "('Hähnchen', 'Pute', 'Rind', 'Schwein', 'Lamm', 'Kalb', 'Wild', 'Fisch', 'Meeresfrüchte')), "
                + "FOREIGN KEY (inventory_item_animal_item_id) REFERENCES inventory_item(inventory_item_id))");
    }

    @Test
    void addsEverythingKeepsExistingRowsAsCertainAndIsReRunnable() {
        backToAfter0011();
        int mince = TagFixtures.item("gemischtes hackfleisch", "meat", "fresh meat");
        TagFixtures.animal(mince, "Rind");
        TagFixtures.animal(mince, "Schwein");
        int recipe = TagFixtures.recipe("Altes Rezept", 10, 10, null, null);
        TagFixtures.ingredient(recipe, mince);
        assertThrows(RuntimeException.class, () -> TagFixtures.animal(mince, "Ente"), "not in the old list");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // once more: re-runnable

        assertEquals(2, count("SELECT COUNT(*) FROM inventory_item_animal"), "no row lost");
        assertEquals(2, count("SELECT COUNT(*) FROM inventory_item_animal WHERE inventory_item_animal_certainty = 'certain'"));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item"));
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_alcohol"), "every item starts without alcohol");
        assertEquals(1, count("SELECT COUNT(*) FROM recipe"));
        assertEquals(1, count("SELECT COUNT(*) FROM recipe_ingredient"));
        assertEquals(0, count("SELECT COUNT(*) FROM recipe_unmatched_ingredient"), "the table starts empty");
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'recipe_unmatched_ingredient' AND INDEX_NAME = 'recipe_unmatched_ingredient_by_recipe'"));
        checks();
    }

    @Test
    void freshSchemaHasTheSameChecks() {
        TestDatabase.reset();
        checks();
    }

    private static void checks() {
        int item = TagFixtures.item("entenbrust", "meat", "fresh meat");
        TagFixtures.animal(item, "Ente");
        TagFixtures.animalWithCertainty(item, "Gans", "possible");
        for (String animal : List.of("Geflügel", "Kaninchen", "Pferd")) {
            TagFixtures.animal(item, animal);
        }
        assertThrows(RuntimeException.class, () -> TagFixtures.animal(item, "Einhorn"));
        assertThrows(RuntimeException.class, () -> TagFixtures.animalWithCertainty(item, "Rind", "maybe"));
        assertThrows(RuntimeException.class, () -> TagFixtures.animal(item, "Ente"), "primary key: item and animal");
        assertEquals("certain", certainty(item, "Ente"), "the default is certain");
        assertEquals("possible", certainty(item, "Gans"));

        TagFixtures.alcohol(item);
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_alcohol"));

        int recipe = TagFixtures.recipe("Test", 1, 1, null, null);
        TagFixtures.unmatched(recipe, 3, "50 g Speck", "unmatched");
        TagFixtures.unmatched(recipe, null, "(Seite nicht im Cache)", "unchecked");
        assertThrows(RuntimeException.class, () -> TagFixtures.unmatched(recipe, 1, "x", "robot"));
        assertThrows(RuntimeException.class, () -> TagFixtures.unmatched(999_999, 1, "x", "unmatched"), "foreign key to the recipe");
        assertThrows(RuntimeException.class, () -> TagFixtures.unmatched(recipe, 1, null, "unmatched"), "raw text is required");
        assertEquals(2, count("SELECT COUNT(*) FROM recipe_unmatched_ingredient"));
        assertEquals(2, count("SELECT COUNT(*) FROM recipe_unmatched_ingredient WHERE recipe_unmatched_ingredient_created_at IS NOT NULL"));
    }

    private static String certainty(int item, String animal) {
        return new ItemAnimalRepository().findAnimalCertainty().get(item).get(animal);
    }
}
