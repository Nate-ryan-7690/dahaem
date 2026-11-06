package de.dahaem.shopping;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.mealplanner.MealPlanRepository;
import de.dahaem.mealplanner.MealPlanService;
import de.dahaem.mealplanner.PlannedMeal;
import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ShoppingListService.generateFromMealPlanByServings against a throwaway MariaDB (Testcontainers): every planned
 * meal is scaled by its own servings (meal, recipe preference, household head count, 2), a recipe planned twice
 * counts twice, and a recipe stored for 2 and cooked for 2 is taken as written.
 */
class ShoppingListByServingsTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    private MealPlanService plans;
    private ShoppingListService service;

    @BeforeEach
    void setUp() throws SQLException {
        TestDatabase.reset();
        plans = new MealPlanService(new MealPlanRepository());
        service = new ShoppingListService(new ShoppingListRepository(), new MealPlanRepository(),
                new InventoryRepository());
        // one item (onion, none in stock) that a store sells, so it lands on the persisted list
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, "
                + "inventory_item_earliest_exp_date) VALUES (1, 'Onion', 'Zwiebel', 0, 'g', 'Gemüse', NULL, NULL)");
        exec("INSERT INTO store (store_id, store_name) VALUES (1, 'Aldi')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (1, 'Bio')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, "
                + "store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available, "
                + "store_item_deposit, store_item_store_product_id, store_item_external_id) "
                + "VALUES (1, 1, 1, 1, 'g', 500, 0.99, 1, NULL, NULL, NULL)");
    }

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void aRecipeStoredForTwoAndCookedForTwoIsTakenAsWritten() throws SQLException {
        recipe(1, 2, 100);
        int plan = planWith(meal(1, null));

        assertEquals(100, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, null)),
                "no household head count: the default 2, factor 1");
    }

    @Test
    void theHouseholdHeadCountScalesMealsThatHaveNoNumberOfTheirOwn() throws SQLException {
        recipe(1, 2, 100);
        int plan = planWith(meal(1, null));

        assertEquals(200, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, 4)));
    }

    @Test
    void aMealsOwnNumberBeatsTheHousehold() throws SQLException {
        recipe(1, 2, 100);
        int plan = planWith(meal(1, 6));

        assertEquals(300, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, 4)));
    }

    @Test
    void theRecipePreferenceBeatsTheHouseholdButNotTheMeal() throws SQLException {
        recipe(1, 2, 100);
        recipe(2, 2, 100);
        exec("UPDATE recipe SET recipe_preferred_servings = 3 WHERE recipe_id IN (1, 2)");
        int plan = planWith(meal(1, null));

        assertEquals(150, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, 4)));
        plans.addMeal(2, MON.plusDays(1), null, 5, null, null);   // own number 5 on recipe 2: 250 more
        assertEquals(400, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, 4)));
    }

    @Test
    void aRecipePlannedTwiceCountsTwice() throws SQLException {
        recipe(1, 2, 100);
        int plan = planWith(meal(1, null));
        plans.addMeal(1, MON.plusDays(2), null, null, null, null);

        assertEquals(200, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, null)));
        assertEquals(Map.of(1, 4), service.servingsPerRecipe(plan, null));
    }

    @Test
    void servingsOfTheSameRecipeAreAddedUpAcrossMeals() throws SQLException {
        recipe(1, 2, 100);
        int plan = planWith(meal(1, 2));
        plans.addMeal(1, MON.plusDays(1), null, 4, null, null);

        assertEquals(Map.of(1, 6), service.servingsPerRecipe(plan, 9));
        assertEquals(300, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, 9)));
    }

    @Test
    void aRecipeStoredForFourCookedForTwoHalves() throws SQLException {
        recipe(1, 4, 400);
        int plan = planWith(meal(1, null));

        assertEquals(200, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, null)));
    }

    @Test
    void differentRecipesWithTheSameItemAreSummed() throws SQLException {
        recipe(1, 2, 100);
        recipe(2, 4, 400);
        int plan = planWith(meal(1, 4));
        plans.addMeal(2, MON.plusDays(1), null, 2, null, null);

        // 100 * 4/2 + 400 * 2/4
        assertEquals(400, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, null)));
    }

    @Test
    void aMealMadeByTheOldEndpointCountsAsOneMealAtTheDefault() throws SQLException {
        recipe(1, 2, 100);
        int plan = plans.createWeeklyPlan(MON);
        plans.addRecipe(plan, 1, "Abendessen");

        assertEquals(100, onionOnList(service.generateFromMealPlanByServings(plan, TODAY, null)));
    }

    @Test
    void anEmptyPlanGivesAnEmptyList() throws SQLException {
        int plan = plans.createWeeklyPlan(MON);

        int listId = service.generateFromMealPlanByServings(plan, TODAY, 4);

        assertEquals(0, count("SELECT COUNT(*) FROM shopping_list_item WHERE shopping_list_id = " + listId));
    }

    @Test
    void theOlderEntryPointsStillBehaveAsBefore() throws SQLException {
        recipe(1, 2, 100);
        int plan = planWith(meal(1, 6));   // the meal's own 6 is ignored by the old methods

        assertEquals(100, onionOnList(service.generateFromMealPlan(plan, TODAY)));
        assertEquals(400, onionOnList(service.generateFromMealPlan(plan, TODAY, 8)));
    }

    // ---------- helpers ----------

    private record MealSpec(int recipeId, Integer servings) {
    }

    private static MealSpec meal(int recipeId, Integer servings) {
        return new MealSpec(recipeId, servings);
    }

    /** A plan for the week of MON with one meal on Monday. */
    private int planWith(MealSpec first) {
        PlannedMeal meal = plans.addMeal(first.recipeId(), MON, null, first.servings(), null, null);
        return meal.getPlanId();
    }

    private static void recipe(int id, int servings, int onionGrams) throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) "
                + "VALUES (" + id + ", 'R" + id + "', " + servings + ", 20, 10)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, "
                + "recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (1, " + id + ", 'g', "
                + onionGrams + ")");
    }

    /** The quantity needed of the onion on the generated list. */
    private static int onionOnList(int listId) throws SQLException {
        return count("SELECT shopping_list_item_quantity_needed FROM shopping_list_item WHERE shopping_list_id = "
                + listId + " AND shopping_list_item_inventory_item_id = 1");
    }

    private static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
