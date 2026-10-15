package de.dahaem.suggestions;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises SuggestionService (and, through it, SuggestionRepository) against a real, throwaway
 * MariaDB database built from the subset of schema.sql it needs (recipe/recipe_ingredient/
 * inventory_item/brand/store/store_item/store_discount). The repository under test is never
 * mocked: only Database's target is swapped out, same approach as PurchaseServiceTest.
 */
class SuggestionServiceTest {

    private SuggestionService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        service = new SuggestionService(new SuggestionRepository());
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void suggestByExpiringRanksARecipeUsingExpiringItemsAboveOneThatDoesNot() throws SQLException {
        LocalDate today = LocalDate.now();
        seedRecipe(1, "Zwiebelsuppe");
        seedRecipe(2, "Apfelkuchen");
        seedInventoryItem(10, "Onion", today.plusDays(2));   // expiring soon
        seedInventoryItem(11, "Flour", today.plusDays(90));  // not expiring soon
        seedIngredient(10, 1);
        seedIngredient(11, 2);

        List<RecipeSuggestion> suggestions = service.suggestByExpiring(7);

        assertEquals(1, suggestions.size(), "only the recipe with an expiring ingredient should be surfaced");
        RecipeSuggestion top = suggestions.get(0);
        assertEquals(1, top.getRecipeId());
        assertEquals("Zwiebelsuppe", top.getRecipeName());
        assertEquals(1, top.getScore());
        assertTrue(top.getMatchingItemIds().contains(10));
    }

    @Test
    void suggestByExpiringRanksHigherMatchCountsFirst() throws SQLException {
        LocalDate today = LocalDate.now();
        seedRecipe(1, "OneMatch");
        seedRecipe(2, "TwoMatches");
        seedInventoryItem(10, "Onion", today.plusDays(1));
        seedInventoryItem(11, "Carrot", today.plusDays(1));
        seedInventoryItem(12, "Celery", today.plusDays(1));
        seedIngredient(10, 1);
        seedIngredient(11, 2);
        seedIngredient(12, 2);

        List<RecipeSuggestion> suggestions = service.suggestByExpiring(7);

        assertEquals(2, suggestions.size());
        assertEquals(2, suggestions.get(0).getRecipeId(), "the recipe with more matching expiring items ranks first");
        assertEquals(2, suggestions.get(0).getScore());
        assertEquals(1, suggestions.get(1).getRecipeId());
        assertEquals(1, suggestions.get(1).getScore());
    }

    @Test
    void suggestByDiscountSurfacesARecipeWithAnOnSaleIngredient() throws SQLException {
        LocalDate today = LocalDate.now();
        seedRecipe(1, "Nudelauflauf");
        seedInventoryItem(20, "Pasta", null);
        seedIngredient(20, 1);
        seedStore(1, "Aldi");
        seedBrand(1, "TestBrand");
        seedStoreItem(100, 1, 1, 20);
        seedDiscount(1000, 100, today.minusDays(1), today.plusDays(3));

        List<RecipeSuggestion> suggestions = service.suggestByDiscount();

        assertEquals(1, suggestions.size());
        RecipeSuggestion suggestion = suggestions.get(0);
        assertEquals(1, suggestion.getRecipeId());
        assertEquals("Nudelauflauf", suggestion.getRecipeName());
        assertEquals(1, suggestion.getScore());
        assertTrue(suggestion.getMatchingItemIds().contains(20));
    }

    @Test
    void suggestByDiscountExcludesRecipesWithoutACurrentDiscount() throws SQLException {
        LocalDate today = LocalDate.now();
        seedRecipe(1, "OnSale");
        seedRecipe(2, "NoDiscount");
        seedRecipe(3, "ExpiredDiscount");
        seedInventoryItem(20, "Pasta", null);
        seedInventoryItem(21, "Rice", null);
        seedInventoryItem(22, "Beans", null);
        seedIngredient(20, 1);
        seedIngredient(21, 2);
        seedIngredient(22, 3);
        seedStore(1, "Aldi");
        seedBrand(1, "TestBrand");
        seedStoreItem(100, 1, 1, 20);
        seedStoreItem(101, 1, 1, 21);
        seedStoreItem(102, 1, 1, 22);
        seedDiscount(1000, 100, today.minusDays(1), today.plusDays(3));       // current
        seedDiscount(1002, 102, today.minusDays(30), today.minusDays(10));    // already expired

        List<RecipeSuggestion> suggestions = service.suggestByDiscount();

        assertEquals(1, suggestions.size(), "only the recipe with a currently-discounted ingredient is surfaced");
        assertEquals(1, suggestions.get(0).getRecipeId());
        assertTrue(suggestions.stream().noneMatch(s -> s.getRecipeId() == 2),
                "a recipe with no discounted ingredients at all must be excluded");
        assertTrue(suggestions.stream().noneMatch(s -> s.getRecipeId() == 3),
                "a recipe whose only discount has already ended must be excluded");
    }

    @Test
    void suggestByDiscountTreatsTheStartAndEndDatesAsInclusive() throws SQLException {
        LocalDate today = LocalDate.now();
        seedRecipe(1, "StartsToday");
        seedRecipe(2, "EndsToday");
        seedInventoryItem(20, "Pasta", null);
        seedInventoryItem(21, "Rice", null);
        seedIngredient(20, 1);
        seedIngredient(21, 2);
        seedStore(1, "Aldi");
        seedBrand(1, "TestBrand");
        seedStoreItem(100, 1, 1, 20);
        seedStoreItem(101, 1, 1, 21);
        seedDiscount(1000, 100, today, today.plusDays(5));
        seedDiscount(1001, 101, today.minusDays(5), today);

        List<RecipeSuggestion> suggestions = service.suggestByDiscount();

        assertEquals(2, suggestions.size());
        assertTrue(suggestions.stream().anyMatch(s -> s.getRecipeId() == 1));
        assertTrue(suggestions.stream().anyMatch(s -> s.getRecipeId() == 2));
    }

    private void seedRecipe(int id, String name) throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (" + id + ", '" + name + "', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");
    }

    private void seedInventoryItem(int id, String name, LocalDate earliestExpDate) throws SQLException {
        String exp = earliestExpDate == null ? "NULL" : "'" + earliestExpDate + "'";
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (" + id + ", '" + name + "', NULL, 1, 'pcs', 'Misc', NULL, " + exp + ")");
    }

    private void seedIngredient(int itemId, int recipeId) throws SQLException {
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (" + itemId + ", " + recipeId + ", 'pcs', 1)");
    }

    private void seedStore(int id, String name) throws SQLException {
        exec("INSERT INTO store (store_id, store_name) VALUES (" + id + ", '" + name + "')");
    }

    private void seedBrand(int id, String name) throws SQLException {
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (" + id + ", '" + name + "')");
    }

    private void seedStoreItem(int storeItemId, int brandId, int storeId, int itemId) throws SQLException {
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, " +
                "store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, " +
                "store_item_is_available, store_item_deposit) VALUES (" +
                storeItemId + ", " + brandId + ", " + storeId + ", " + itemId + ", 'pcs', 1, 1.00, 1, NULL)");
    }

    private void seedDiscount(int id, int storeItemId, LocalDate start, LocalDate end) throws SQLException {
        exec("INSERT INTO store_discount (store_discount_id, store_discount_store_item_id, " +
                "store_discount_start_date, store_discount_end_date, store_discount_price, store_discount_notes) " +
                "VALUES (" + id + ", " + storeItemId + ", '" + start + "', '" + end + "', 1.00, NULL)");
    }

    private void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

}
