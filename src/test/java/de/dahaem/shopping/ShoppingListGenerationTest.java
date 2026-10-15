package de.dahaem.shopping;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.mealplanner.MealPlanRepository;
import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
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
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises ShoppingListService.generateFromMealPlan against a real, throwaway MariaDB database built
 * from the subset of schema.sql it needs (meal_plan/meal_plan_recipe, recipe/recipe_ingredient,
 * inventory_item, and the store tables, plus shopping_list/shopping_list_item themselves, all with
 * the exact column definitions schema.sql declares). The repository under test is never mocked:
 * only Database's target is swapped out.
 */
class ShoppingListGenerationTest {

    private ShoppingListService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        service = new ShoppingListService(new ShoppingListRepository(), new MealPlanRepository(),
                new InventoryRepository());
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void producesOnlyTheMissingQuantityAfterSubtractingInventory() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Onion', 'Zwiebel', 100, 'g', 'Gemüse', NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings) VALUES (1, 'Suppe', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, 1, 'g', 250)");

        exec("INSERT INTO store (store_id, store_name) VALUES (1, 'Aldi')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (1, 'Bio')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (1, 1, 1, 1, 'g', 500, 0.99, 1, NULL, NULL, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);

        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));

        assertEquals(1, countRows("shopping_list_item WHERE shopping_list_id = " + shoppingListId));
        assertEquals(150, singleInt("SELECT shopping_list_item_quantity_needed FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId));
        assertNull(singleObject("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                        + "WHERE shopping_list_id = " + shoppingListId),
                "no store item is auto-picked at generation time; the user chooses later");
    }

    @Test
    void generateFromMealPlanScalesQuantitiesByPeopleOverRecipeServings() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Onion', 'Zwiebel', 0, 'g', 'Gemüse', NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings) VALUES (1, 'Suppe', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, 1, 'g', 100)");

        exec("INSERT INTO store (store_id, store_name) VALUES (1, 'Aldi')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (1, 'Bio')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (1, 1, 1, 1, 'g', 500, 0.99, 1, NULL, NULL, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);

        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25), 4);

        assertEquals(1, countRows("shopping_list_item WHERE shopping_list_id = " + shoppingListId));
        assertEquals(200, singleInt("SELECT shopping_list_item_quantity_needed FROM shopping_list_item "
                        + "WHERE shopping_list_id = " + shoppingListId),
                "a 2-serving recipe's 100g ingredient must double at people=4");
    }

    @Test
    void excludesAnItemAlreadyFullyInStock() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Onion', 'Zwiebel', 500, 'g', 'Gemüse', NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (2, 'Pasta', 'Nudeln', 0, 'g', 'Trockenware', NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings) VALUES (1, 'Pasta al Pomodoro', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, 1, 'g', 250)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (2, 1, 'g', 400)");

        exec("INSERT INTO store (store_id, store_name) VALUES (1, 'Aldi')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (1, 'Bio')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (1, 1, 1, 1, 'g', 500, 0.99, 1, NULL, NULL, NULL)");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (2, 1, 1, 2, 'g', 500, 1.49, 1, NULL, NULL, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);

        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));

        assertEquals(1, countRows("shopping_list_item WHERE shopping_list_id = " + shoppingListId),
                "onion is fully in stock (500 >= 250) and must be dropped; only pasta remains");
        assertNull(singleObject("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                        + "WHERE shopping_list_id = " + shoppingListId),
                "no store item is auto-picked at generation time; the user chooses later");
        assertEquals(400, singleInt("SELECT shopping_list_item_quantity_needed FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId));
    }

    @Test
    void persistsTheShoppingListLinkedToTheMealPlanWithItsItems() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Pasta', 'Nudeln', 100, 'g', 'Trockenware', NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings) VALUES (1, 'Pasta al Pomodoro', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, 1, 'g', 500)");

        exec("INSERT INTO store (store_id, store_name) VALUES (2, 'Rewe')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (2, 'Barilla')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (3, 2, 2, 1, 'g', 500, 1.49, 1, 0.0, NULL, NULL)");
        exec("INSERT INTO store_discount (store_discount_id, store_discount_store_item_id, store_discount_start_date, store_discount_end_date, store_discount_price, store_discount_notes, store_discount_deal_quantity, store_discount_deal_price) VALUES (1, 3, '2026-09-20', '2026-09-30', 0.99, 'promo', 1, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);

        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));

        assertEquals(1, countRows("shopping_list WHERE shopping_list_id = " + shoppingListId));
        assertEquals(planId, singleInt("SELECT shopping_list_meal_plan_id FROM shopping_list "
                + "WHERE shopping_list_id = " + shoppingListId));
        assertEquals("2026-09-25", singleString("SELECT shopping_list_created_date FROM shopping_list "
                + "WHERE shopping_list_id = " + shoppingListId));

        assertEquals(1, countRows("shopping_list_item WHERE shopping_list_id = " + shoppingListId));
        assertEquals(400, singleInt("SELECT shopping_list_item_quantity_needed FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId));
        assertEquals(1, singleInt("SELECT shopping_list_item_inventory_item_id FROM shopping_list_item "
                        + "WHERE shopping_list_id = " + shoppingListId),
                "the persisted line must record which inventory item it is for");
        assertNull(singleObject("SELECT shopping_list_item_is_discounted FROM shopping_list_item "
                        + "WHERE shopping_list_id = " + shoppingListId),
                "no store option is chosen yet at generation time, so whether it's on sale is unknown");
        assertFalse(singleBoolean("SELECT shopping_list_item_purchase_status FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId), "a freshly generated item is still pending");
        assertFalse(singleBoolean("SELECT shopping_list_item_is_manual FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId));
    }

    @Test
    void selectingAStoreItemPersistsTheChoiceAndClearingItPersistsToo() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Pasta', 'Nudeln', 0, 'g', 'Trockenware', NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings) VALUES (1, 'Pasta al Pomodoro', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, 1, 'g', 500)");

        exec("INSERT INTO store (store_id, store_name) VALUES (2, 'Rewe')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (2, 'Barilla')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (3, 2, 2, 1, 'g', 500, 1.49, 1, 0.0, NULL, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);
        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));
        int itemId = singleInt("SELECT shopping_list_item_id FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId);

        assertEquals(shoppingListId, service.findShoppingListIdForItem(itemId).orElseThrow());

        service.selectStoreItem(itemId, 3);
        assertEquals(3, singleInt("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                + "WHERE shopping_list_item_id = " + itemId));

        service.selectStoreItem(itemId, null);
        assertNull(singleObject("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                + "WHERE shopping_list_item_id = " + itemId), "passing null clears the selection");
    }

    @Test
    void dropsAnItemNoStoreCurrentlySells() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Saffron', 'Safran', 0, 'g', 'Gewürze', NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings) VALUES (1, 'Paella', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, 1, 'g', 5)");
        // deliberately no store/brand/store_item: nothing currently sells this item

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);

        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));

        assertEquals(0, countRows("shopping_list_item WHERE shopping_list_id = " + shoppingListId),
                "nothing currently sells the item, so there is nothing meaningful to persist");
    }

    @Test
    void rollsBackTheWholeListOnFailure() throws SQLException {
        int bogusMealPlanId = 999_999; // violates shopping_list_meal_plan_id's foreign key

        assertThrows(RuntimeException.class,
                () -> service.generateFromMealPlan(bogusMealPlanId, LocalDate.of(2026, 9, 25)));

        assertEquals(0, countRows("shopping_list WHERE shopping_list_meal_plan_id = " + bogusMealPlanId),
                "the failed generation must not leave a partially-committed shopping_list row behind");
    }

    @Test
    void loadPersistedListReturnsNamesOptionsAndReflectsAPriorSelection() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Pasta', 'Nudeln', 0, 'g', 'Trockenware', NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings) VALUES (1, 'Pasta al Pomodoro', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, 1, 'g', 500)");

        exec("INSERT INTO store (store_id, store_name) VALUES (2, 'Rewe')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (2, 'Barilla')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (3, 2, 2, 1, 'g', 500, 1.49, 1, 0.0, NULL, NULL)");
        exec("INSERT INTO store (store_id, store_name) VALUES (3, 'Aldi')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (3, 'Combino')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (4, 3, 3, 1, 'g', 500, 1.19, 1, NULL, NULL, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);
        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));
        int itemId = singleInt("SELECT shopping_list_item_id FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId);

        service.selectStoreItem(itemId, 4);

        PersistedShoppingList persisted = service.loadPersistedList(shoppingListId, LocalDate.of(2026, 9, 25))
                .orElseThrow();

        assertEquals(shoppingListId, persisted.id());
        assertEquals(planId, persisted.mealPlanId());
        assertEquals(1, persisted.items().size());

        PersistedShoppingListItem item = persisted.items().get(0);
        assertEquals(itemId, item.shoppingListItemId());
        assertEquals(1, item.inventoryItemId());
        assertEquals("Nudeln", item.nameDeutsch());
        assertEquals("Pasta", item.nameEnglish());
        assertEquals(500, item.quantityNeeded());
        assertEquals(2, item.storeOptions().size(), "both current store options must be surfaced");
        assertEquals(4, item.selectedStoreItemId(), "reload must reflect the prior selection");

        assertTrue(service.loadPersistedList(999_999).isEmpty(), "an unknown list id must come back empty");
    }

    @Test
    void selectStoreItemRejectsAStoreItemThatIsNotACandidateForTheLine() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (1, 'Pasta', 'Nudeln', 0, 'g', 'Trockenware', NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (2, 'Milk', 'Milch', 0, 'ml', 'Milchprodukte', NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings) VALUES (1, 'Pasta al Pomodoro', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, 1, 'g', 500)");

        exec("INSERT INTO store (store_id, store_name) VALUES (2, 'Rewe')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (2, 'Barilla')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (3, 2, 2, 1, 'g', 500, 1.49, 1, 0.0, NULL, NULL)"); // pasta's own option
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, store_item_deposit, store_item_store_product_id, store_item_external_id) VALUES (4, 2, 2, 2, 'ml', 500, 0.89, 1, NULL, NULL, NULL)"); // a different item

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);
        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));
        int itemId = singleInt("SELECT shopping_list_item_id FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId);

        assertThrows(IllegalArgumentException.class, () -> service.selectStoreItem(itemId, 4),
                "store_item 4 sells milk, not pasta -- must not be accepted for this line");
        assertNull(singleObject("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                + "WHERE shopping_list_item_id = " + itemId), "the rejected pick must not be persisted");

        service.selectStoreItem(itemId, 3); // the actually-matching option still works
        assertEquals(3, singleInt("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                + "WHERE shopping_list_item_id = " + itemId));
    }

    private int createPlan(LocalDate weekStart) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO meal_plan (meal_plan_week_start_date) VALUES (?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, weekStart.toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void addRecipeToPlan(int planId, int recipeId) throws SQLException {
        exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, "
                + "meal_plan_recipe_meal_time) VALUES (" + recipeId + ", " + planId + ", 'dinner')");
    }

    /**
     * Seed rows only fill the columns these tests care about; the real schema has further NOT NULL
     * columns without defaults, so strict mode is relaxed for the seed insert (and restored before the
     * connection goes back into the pool). The code under test still runs under the server's strict mode.
     */
    private void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute("SET @previous_sql_mode = @@SESSION.sql_mode");
            st.execute("SET SESSION sql_mode = ''");
            try {
                st.execute(sql);
            } finally {
                st.execute("SET SESSION sql_mode = @previous_sql_mode");
            }
        }
    }

    private int countRows(String fromAndWhere) throws SQLException {
        return singleInt("SELECT COUNT(*) FROM " + fromAndWhere);
    }

    private int singleInt(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String singleString(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private Object singleObject(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1);
        }
    }

    private boolean singleBoolean(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

}
