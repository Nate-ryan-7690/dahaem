package de.dahaem.tags;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static de.dahaem.tags.TagFixtures.count;
import static de.dahaem.tags.TagFixtures.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * migrations/0013_catalog_meat_lines.sql on a throwaway MariaDB (Testcontainers): it adds two items with their animal
 * (certain), adds the Rind row for the reused Rindergeschnetzeltes, moves two vegetarian canned dishes out of 'canned
 * meat' guarded on the current value, changes nothing else, and can be run twice. After it, a recipe with only the
 * two dishes is vegetarisch.
 */
class CatalogMeatLinesMigrationTest {

    private static final Path MIGRATION = Path.of("migrations", "0013_catalog_meat_lines.sql");

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
        item(877, "chili sin carne dose", "canned & jarred goods", "canned meat");
        item(887, "gemüsetopf dose", "canned & jarred goods", "canned meat");
        item(883, "kartoffeltopf dose", "canned & jarred goods", "canned meat");
        item(1660, "Rindergeschnetzeltes", "meat & poultry", null);
        item(300, "wildschwein", "meat", "fresh meat");
    }

    @Test
    void addsTheItemsAndAnimalsCorrectsTwoCategoriesAndIsReRunnable() {
        liveBefore();
        int itemsBefore = count("SELECT COUNT(*) FROM inventory_item");

        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // once more: re-runnable

        assertEquals(itemsBefore + 2, count("SELECT COUNT(*) FROM inventory_item"), "only two new items");
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 1822 "
                + "AND inventory_item_name_deutsch = 'Hirschsteak' AND inventory_item_category = 'meat' "
                + "AND inventory_item_subcategory = 'fresh meat' AND inventory_item_unit = 'g'"));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 1823 "
                + "AND inventory_item_name_deutsch = 'Schweinsbraten-Aufschnitt' AND inventory_item_category = 'meat' "
                + "AND inventory_item_subcategory = 'wurst & aufschnitt'"));
        assertEquals(3, count("SELECT COUNT(*) FROM inventory_item_animal"));
        for (String[] row : new String[][]{{"1822", "Wild"}, {"1823", "Schwein"}, {"1660", "Rind"}}) {
            assertEquals(1, count("SELECT COUNT(*) FROM inventory_item_animal WHERE inventory_item_animal_item_id = "
                    + row[0] + " AND inventory_item_animal_animal = '" + row[1]
                    + "' AND inventory_item_animal_certainty = 'certain'"));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 877 "
                + "AND inventory_item_subcategory = 'canned legumes'"));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 887 "
                + "AND inventory_item_subcategory = 'canned vegetables'"));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 883 "
                + "AND inventory_item_subcategory = 'canned meat'"), "the other canned stews are not part of it");
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 300 "
                + "AND inventory_item_subcategory = 'fresh meat'"));
    }

    @Test
    void aCorrectionOnlyAppliesToTheCurrentValue() {
        liveBefore();
        run("UPDATE inventory_item SET inventory_item_subcategory = 'ready meals' WHERE inventory_item_id = 877");
        TestDatabase.apply(List.of(MIGRATION));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 877 "
                + "AND inventory_item_subcategory = 'ready meals'"), "someone already moved it: left alone");
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 887 "
                + "AND inventory_item_subcategory = 'canned vegetables'"));
    }

    @Test
    void theFinalSelectSaysOkForEveryLine() throws Exception {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        assertEquals(0, rows("CHECK"), "no CHECK line");
        assertEquals(5, rows("OK"));
    }

    @Test
    void theTwoDishesNoLongerKeepARecipeFromBeingVegetarian() {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        assertFalse(AnimalProducts.vegetarianBlocker(new CatalogItem(877, "chili sin carne dose",
                "canned & jarred goods", "canned legumes"), false).isPresent());
        assertFalse(AnimalProducts.vegetarianBlocker(new CatalogItem(887, "gemüsetopf dose",
                "canned & jarred goods", "canned vegetables"), false).isPresent());
        assertTrue(AnimalProducts.vegetarianBlocker(new CatalogItem(883, "kartoffeltopf dose",
                "canned & jarred goods", "canned meat"), false).isPresent());
    }

    /** Runs the migration's last statement again and counts result lines starting with the prefix. */
    private static int rows(String prefix) throws Exception {
        String sql = new String(java.nio.file.Files.readAllBytes(MIGRATION), java.nio.charset.StandardCharsets.UTF_8);
        String select = sql.substring(sql.lastIndexOf("SELECT e.what"));
        int n = 0;
        try (java.sql.Connection con = de.dahaem.shared.Database.getConnection();
             java.sql.Statement st = con.createStatement();
             java.sql.ResultSet rs = st.executeQuery(select.substring(0, select.lastIndexOf(';')))) {
            while (rs.next()) {
                if (rs.getString("result").startsWith(prefix)) {
                    n++;
                }
            }
        }
        return n;
    }
}
