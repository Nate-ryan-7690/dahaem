package de.dahaem.inventory;

import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises InventoryService (and, through it, InventoryRepository) against a real, throwaway
 * MariaDB database built from just the inventory_item table. The repository under test is never
 * mocked: only Database's target is swapped out, same approach as ShoppingListServiceTest.
 */
class InventoryServiceTest {

    private InventoryService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        service = new InventoryService(new InventoryRepository());
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void totalItemCountIsZeroForAnEmptyTable() {
        assertEquals(0, service.totalItemCount());
    }

    @Test
    void totalItemCountMatchesTheNumberOfSeededRows() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Onion', 'Zwiebel', 4, 'pcs', 'Vegetables', NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (2, 'Milk', 'Milch', 1, 'l', 'Dairy', 'Fresh', NULL)");

        assertEquals(2, service.totalItemCount());
    }

    @Test
    void allItemsMapsEveryColumnAndSortsByEnglishName() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Zucchini', NULL, 3, 'pcs', 'Vegetables', NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (2, 'Apple', 'Apfel', 6, 'pcs', 'Fruit', 'Pome', '2026-10-05')");

        List<Item> items = service.allItems();

        assertEquals(2, items.size());
        Item apple = items.get(0);
        assertEquals("Apple", apple.getNameEnglish(), "results must be ordered by english name");
        assertEquals(2, apple.getId());
        assertEquals("Apfel", apple.getNameDeutsch());
        assertEquals(6, apple.getQuantity());
        assertEquals("pcs", apple.getUnit());
        assertEquals("Fruit", apple.getCategory());
        assertEquals("Pome", apple.getSubcategory());
        assertEquals(LocalDate.of(2026, 10, 5), apple.getEarliestExpDate());

        Item zucchini = items.get(1);
        assertEquals("Zucchini", zucchini.getNameEnglish());
        assertNull(zucchini.getNameDeutsch());
        assertNull(zucchini.getSubcategory());
        assertNull(zucchini.getEarliestExpDate());
    }

    @Test
    void lowStockKeepsItemsAtOrBelowTheThresholdAndExcludesItemsAboveIt() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Flour', 'Mehl', 5, 'kg', 'Baking', NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (2, 'Sugar', 'Zucker', 6, 'kg', 'Baking', NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (3, 'Salt', 'Salz', 0, 'kg', 'Baking', NULL, NULL)");

        List<Item> lowStock = service.lowStock(5);

        assertEquals(2, lowStock.size(), "threshold is inclusive, so quantity 5 and 0 stay, 6 is filtered out");
        assertTrue(lowStock.stream().anyMatch(i -> i.getId() == 1));
        assertTrue(lowStock.stream().anyMatch(i -> i.getId() == 3));
        assertTrue(lowStock.stream().noneMatch(i -> i.getId() == 2));
    }

    @Test
    void expiringWithinIgnoresItemsWithNoExpiryDateAndExcludesItemsPastTheWindow() throws SQLException {
        LocalDate today = LocalDate.now();
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'NoExpiry', NULL, 1, 'pcs', 'Misc', NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (2, 'AtLimit', NULL, 1, 'pcs', 'Misc', NULL, '" + today.plusDays(7) + "')");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (3, 'BeyondLimit', NULL, 1, 'pcs', 'Misc', NULL, '" + today.plusDays(8) + "')");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (4, 'AlreadyExpired', NULL, 1, 'pcs', 'Misc', NULL, '" + today.minusDays(1) + "')");

        List<Item> expiring = service.expiringWithin(7);

        assertEquals(2, expiring.size());
        assertTrue(expiring.stream().anyMatch(i -> i.getId() == 2), "an item expiring exactly on the limit day must be included");
        assertTrue(expiring.stream().anyMatch(i -> i.getId() == 4), "an already-expired item counts as within the window");
        assertTrue(expiring.stream().noneMatch(i -> i.getId() == 1), "items without an expiry date must be ignored");
        assertTrue(expiring.stream().noneMatch(i -> i.getId() == 3), "an item expiring after the limit must be excluded");
    }

    private void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

}
