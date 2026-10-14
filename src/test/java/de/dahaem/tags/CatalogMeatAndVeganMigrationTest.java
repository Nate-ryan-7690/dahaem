package de.dahaem.tags;

import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static de.dahaem.tags.TagFixtures.count;
import static de.dahaem.tags.TagFixtures.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * migrations/0014_catalog_meat_and_vegan_lines.sql on a throwaway MariaDB (Testcontainers): it adds nine items, their
 * animals (Krakauer as three POSSIBLE ones) and the Schwein rows for the two reused items, changes nothing else, can
 * run twice, and its final SELECT says OK for every line.
 */
class CatalogMeatAndVeganMigrationTest {

    private static final Path MIGRATION = Path.of("migrations", "0014_catalog_meat_and_vegan_lines.sql");

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    private static void item(int id, String name, String category, String subcategory) {
        run("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_unit, inventory_item_category, inventory_item_subcategory) VALUES (?, ?, ?, 'g', ?, ?)",
                id, name + " (en)", name, category, subcategory);
    }

    private static void liveBefore() {
        TestDatabase.reset();
        item(267, "schweinshaxe", "meat", "fresh meat");
        item(263, "schweinebraten", "meat", "fresh meat");
        item(377, "margarine", "dairy", null);
    }

    private static int animals(int item, String certainty) {
        return count("SELECT COUNT(*) FROM inventory_item_animal WHERE inventory_item_animal_item_id = " + item
                + " AND inventory_item_animal_certainty = '" + certainty + "'");
    }

    @Test
    void addsTheItemsAndAnimalsAndIsReRunnable() {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // once more: re-runnable

        assertEquals(3 + 9, count("SELECT COUNT(*) FROM inventory_item"), "only nine new items");
        assertEquals(5, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id BETWEEN 1824 AND 1828 "
                + "AND inventory_item_category = 'plant-based' AND inventory_item_subcategory IS NULL"));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 1831 "
                + "AND inventory_item_subcategory = 'würstchen' AND inventory_item_category = 'meat'"));
        assertEquals(1, animals(1829, "certain"));
        assertEquals(3, animals(1830, "possible"));
        assertEquals(0, animals(1830, "certain"), "Krakauer is generic: no certain animal");
        assertEquals(1, animals(1831, "certain"));
        assertEquals(1, animals(1832, "certain"));
        assertEquals(1, animals(267, "certain"), "reused Grillhaxe item");
        assertEquals(1, animals(263, "certain"), "reused Krustenbraten item");
        for (int vegan = 1824; vegan <= 1828; vegan++) {
            assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal WHERE inventory_item_animal_item_id = "
                    + vegan), "a vegan substitute has no animal");
        }
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 377 "
                + "AND inventory_item_category = 'dairy'"), "existing rows are untouched");
    }

    @Test
    void theFinalSelectSaysOkForEveryLine() throws Exception {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        String select = sql.substring(sql.lastIndexOf("SELECT e.what"));
        select = select.substring(0, select.lastIndexOf(';'));
        int ok = 0;
        int check = 0;
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(select)) {
            while (rs.next()) {
                if (rs.getString("result").equals("OK")) {
                    ok++;
                } else {
                    check++;
                }
            }
        }
        assertEquals(0, check);
        assertEquals(11, ok);
    }

    @Test
    void theFinalSelectFlagsAMissingAnimalRow() throws Exception {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        run("DELETE FROM inventory_item_animal WHERE inventory_item_animal_item_id = 1829");   // throwaway database
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        String select = sql.substring(sql.lastIndexOf("SELECT e.what"));
        select = select.substring(0, select.lastIndexOf(';'));
        int check = 0;
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(select)) {
            while (rs.next()) {
                if (rs.getString("result").startsWith("CHECK")) {
                    check++;
                }
            }
        }
        assertEquals(1, check);
    }

    @Test
    void veganSubstitutesDoNotBlockVegetarianButTheMeatItemsDo() {
        assertFalse(AnimalProducts.vegetarianBlocker(new CatalogItem(1824, "Veganes Filet nach Lachs-Art",
                "plant-based", null), false).isPresent());
        assertFalse(AnimalProducts.vegetarianBlocker(new CatalogItem(1828, "veganes Gyros", "plant-based", null),
                false).isPresent());
        assertFalse(AnimalClassifier.isAnimalCandidate(new CatalogItem(1826, "vegane Knusperstäbchen nach Fisch-Art",
                "plant-based", null)));
        assertTrue(AnimalProducts.vegetarianBlocker(new CatalogItem(1829, "Pastrami", "meat", "wurst & aufschnitt"),
                false).isPresent());
        assertTrue(AnimalProducts.vegetarianBlocker(new CatalogItem(1832, "Hackfleischzubereitung aus Hähnchen",
                "meat", "fresh meat"), false).isPresent());
    }
}
