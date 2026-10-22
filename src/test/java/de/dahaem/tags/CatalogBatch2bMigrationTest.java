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
import java.util.ArrayList;
import java.util.List;

import static de.dahaem.tags.TagFixtures.count;
import static de.dahaem.tags.TagFixtures.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * migrations/0015_catalog_batch_2b.sql on a throwaway MariaDB (Testcontainers): it adds 67 items (ids 1833-1899)
 * and their animals, changes nothing else, can run twice, and its final SELECT says OK for every line.
 */
class CatalogBatch2bMigrationTest {

    private static final Path MIGRATION = Path.of("migrations", "0015_catalog_batch_2b.sql");
    private static final int FIRST = 1833;
    private static final int LAST = 1899;
    private static final int NEW_ITEMS = LAST - FIRST + 1;

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    private static void liveBefore() {
        TestDatabase.reset();
        run("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_unit, inventory_item_category, inventory_item_subcategory) "
                + "VALUES (1832, 'chicken mince preparation', 'Hackfleischzubereitung aus Hähnchen', 'g', 'meat', "
                + "'fresh meat')");
    }

    private static int animals(int item, String certainty) {
        return count("SELECT COUNT(*) FROM inventory_item_animal WHERE inventory_item_animal_item_id = " + item
                + " AND inventory_item_animal_certainty = '" + certainty + "'");
    }

    private static int idOf(String name) {
        return count("SELECT inventory_item_id FROM inventory_item WHERE inventory_item_name_deutsch = '" + name + "'");
    }

    @Test
    void addsTheItemsAndAnimalsAndIsReRunnable() {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));   // once more: re-runnable

        assertEquals(1 + NEW_ITEMS, count("SELECT COUNT(*) FROM inventory_item"), "only the new items");
        assertEquals(NEW_ITEMS, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id BETWEEN "
                + FIRST + " AND " + LAST), "ids are contiguous");
        assertEquals(6, count("SELECT COUNT(*) FROM inventory_item_animal"), "only the six animal rows below");
        assertEquals(1, animals(idOf("Pastrami mit Pfefferrand"), "certain"));
        assertEquals(1, animals(idOf("Ravioli mit Nduja"), "certain"));
        assertEquals(1, animals(idOf("Rindfleisch"), "certain"));
        assertEquals(2, animals(idOf("Brühe"), "possible"), "an unspecified stock is Rind or Hähnchen");
        assertEquals(0, animals(idOf("Brühe"), "certain"));
        assertEquals(1, animals(idOf("Braune Soße"), "possible"));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = 1832 "
                + "AND inventory_item_category = 'meat'"), "existing rows are untouched");
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_alcohol"),
                "no new item is alcoholic");
    }

    @Test
    void everyNewItemIsItsOwnProduct() {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        assertEquals(NEW_ITEMS, count("SELECT COUNT(DISTINCT inventory_item_name_deutsch) FROM inventory_item "
                + "WHERE inventory_item_id BETWEEN " + FIRST + " AND " + LAST), "no name twice");
        // the item rule: varieties and flavours are separate items, generic lines map to the plain one
        for (String name : List.of("Bio-Sommerblütenhonig", "Chipotle-Senf", "Kräutersenf", "Biersenf",
                "Pastrami mit Pfefferrand", "Bunte Karotten", "gekochte Eier", "Spekulatius-Kekse", "Mini-Spekulatius",
                "Frühstücksherzen", "Frühstücksherzen Mehrkorn", "Kaminkäse", "gelbe Bete", "Torchietti",
                "Rindfleisch", "Süße Soße Asiatische Art", "Burger-Sauce", "Bruschetta-Dip")) {
            assertTrue(idOf(name) >= FIRST, name);
        }
    }

    @Test
    void theNewItemsHaveCategoriesTheTaggerKnows() {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        // every non-meat item must be one the vegetarian logic lets through when it has no animal row, and the meat
        // ones (Pastrami, Nduja ravioli, Rindfleisch) and the generic ones with POSSIBLE animals must block it
        List<String> wronglyBlocked = new ArrayList<>();
        List<String> wronglyFree = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT i.inventory_item_id, i.inventory_item_name_deutsch, "
                     + "i.inventory_item_category, COALESCE(i.inventory_item_subcategory, ''), "
                     + "(SELECT COUNT(*) FROM inventory_item_animal a WHERE a.inventory_item_animal_item_id = "
                     + "i.inventory_item_id) FROM inventory_item i WHERE i.inventory_item_id BETWEEN "
                     + FIRST + " AND " + LAST)) {
            while (rs.next()) {
                boolean recorded = rs.getInt(5) > 0;
                String sub = rs.getString(4);
                boolean blocked = AnimalProducts.vegetarianBlocker(new CatalogItem(rs.getInt(1), rs.getString(2),
                        rs.getString(3), sub.isEmpty() ? null : sub), recorded).isPresent();
                if (recorded && !blocked) {
                    wronglyFree.add(rs.getString(2));
                }
                if (!recorded && blocked) {
                    wronglyBlocked.add(rs.getString(2));
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals(List.of(), wronglyBlocked, "meat-free items the vegetarian logic would block");
        assertEquals(List.of(), wronglyFree, "items with a recorded animal that would not block");
    }

    private static List<String> finalSelect(String suffix) throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        String select = sql.substring(sql.lastIndexOf("SELECT e.what"));
        select = select.substring(0, select.lastIndexOf(';'));
        List<String> results = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(select + suffix)) {
            while (rs.next()) {
                results.add(rs.getString("result"));
            }
        }
        return results;
    }

    @Test
    void theFinalSelectSaysOkForEveryLine() throws Exception {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        List<String> results = finalSelect("");
        assertEquals(NEW_ITEMS, results.size());
        assertEquals(List.of(), results.stream().filter(r -> !r.equals("OK")).toList());
    }

    @Test
    void theFinalSelectFlagsAMissingAnimalRow() throws Exception {
        liveBefore();
        TestDatabase.apply(List.of(MIGRATION));
        run("DELETE FROM inventory_item_animal WHERE inventory_item_animal_item_id = " + idOf("Rindfleisch"));
        assertEquals(1, finalSelect("").stream().filter(r -> r.startsWith("CHECK")).count());
    }

    @Test
    void theFinalSelectFlagsAnIdUsedByAnotherItem() throws Exception {
        liveBefore();
        run("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_unit, inventory_item_category) VALUES (1840, 'other', 'etwas anderes', 'g', 'fruit')");
        TestDatabase.apply(List.of(MIGRATION));
        assertEquals(1, finalSelect("").stream().filter(r -> r.startsWith("CHECK")).count());
    }
}
