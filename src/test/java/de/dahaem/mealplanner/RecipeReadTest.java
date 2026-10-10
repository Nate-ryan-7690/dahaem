package de.dahaem.mealplanner;

import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the recipe read slice (RecipeService and, through it and directly, RecipeRepository)
 * against a real, temporary SQLite file built from just the recipe/recipe_ingredient/
 * recipe_instructions/inventory_item tables it needs. Nothing is mocked: only Database's target
 * file is swapped out, same approach as ShoppingListServiceTest.
 *
 * meal_plan and tag/recipe_tag are intentionally out of scope here; separate PRs cover those.
 */
class RecipeReadTest {

    private RecipeRepository repository;
    private RecipeService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        repository = new RecipeRepository();
        service = new RecipeService(repository);
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void totalRecipeCountIsZeroForAnEmptyTable() {
        assertEquals(0, service.totalRecipeCount());
    }

    @Test
    void totalRecipeCountMatchesTheNumberOfSeededRows() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (1, 'Suppe', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (2, 'Eintopf', 2, 45, 15, NULL, NULL, NULL, NULL, NULL, NULL)");

        assertEquals(2, service.totalRecipeCount());
    }

    @Test
    void allRecipesMapsEveryColumnIncludingNullableMacrosAndSortsByName() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (1, 'Zwiebelsuppe', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (2, 'Apfelkuchen', 8, 60, 20, 350, 5, 45, 12, 3, 20)");

        List<Recipe> recipes = service.allRecipes();

        assertEquals(2, recipes.size());
        Recipe apfelkuchen = recipes.get(0);
        assertEquals("Apfelkuchen", apfelkuchen.getName(), "results must be ordered by recipe name");
        assertEquals(2, apfelkuchen.getId());
        assertEquals(8, apfelkuchen.getServings());
        assertEquals(60, apfelkuchen.getCookTime());
        assertEquals(20, apfelkuchen.getPrepTime());
        assertEquals(350, apfelkuchen.getCalories());
        assertEquals(5, apfelkuchen.getProtein());
        assertEquals(45, apfelkuchen.getCarbs());
        assertEquals(12, apfelkuchen.getFat());
        assertEquals(3, apfelkuchen.getFiber());
        assertEquals(20, apfelkuchen.getSugar());

        Recipe zwiebelsuppe = recipes.get(1);
        assertEquals("Zwiebelsuppe", zwiebelsuppe.getName());
        assertNull(zwiebelsuppe.getCalories(), "unset macro columns must map to null, not 0");
        assertNull(zwiebelsuppe.getProtein());
        assertNull(zwiebelsuppe.getSugar());
    }

    @Test
    void findByIdReturnsTheMatchingRecipe() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (7, 'Pfannkuchen', 3, 15, 5, 220, 8, 30, 6, 1, 10)");

        Optional<Recipe> found = repository.findById(7);

        assertTrue(found.isPresent());
        assertEquals("Pfannkuchen", found.get().getName());
        assertEquals(3, found.get().getServings());
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertTrue(repository.findById(999).isEmpty());
    }

    @Test
    void findAllIngredientsJoinsInInventoryItemNames() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (1, 'Suppe', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (10, 'Onion', 'Zwiebel', 2, 'pcs', 'Vegetables', NULL, NULL)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (11, 'Carrot', 'Karotte', 3, 'pcs', 'Vegetables', NULL, NULL)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (10, 1, 'pcs', 2)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (11, 1, 'pcs', 3)");

        List<RecipeIngredient> ingredients = service.findAllIngredients(1);

        assertEquals(2, ingredients.size());
        RecipeIngredient onion = ingredients.stream().filter(i -> i.getItemId() == 10).findFirst().orElseThrow();
        assertEquals(1, onion.getRecipeId());
        assertEquals("pcs", onion.getUnit());
        assertEquals(0, new BigDecimal("2").compareTo(onion.getQuantityNeeded()));
        assertFalse(onion.isToTaste());
        assertEquals("Onion", onion.getNameEnglish());
        assertEquals("Zwiebel", onion.getNameDeutsch());
    }

    @Test
    void findAllIngredientsReadsDecimalAndToTasteRows() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) VALUES (1, 'Suppe', 2, 30, 10)");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category) VALUES (10, 'Lemon', 'Zitrone', 0, 'Stück', 'fruit')");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category) VALUES (11, 'Salt', 'Salz', 0, 'g', 'spices')");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (10, 1, 'Stück', 0.5)");
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) VALUES (11, 1, '', NULL, TRUE)");

        List<RecipeIngredient> ingredients = service.findAllIngredients(1);

        RecipeIngredient lemon = ingredients.stream().filter(i -> i.getItemId() == 10).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.5").compareTo(lemon.getQuantityNeeded()));
        assertFalse(lemon.isToTaste());
        RecipeIngredient salt = ingredients.stream().filter(i -> i.getItemId() == 11).findFirst().orElseThrow();
        assertNull(salt.getQuantityNeeded());
        assertTrue(salt.isToTaste());
    }

    @Test
    void findAllIngredientsReturnsEmptyListForARecipeWithNoIngredients() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (1, 'Suppe', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");

        assertTrue(service.findAllIngredients(1).isEmpty());
    }

    @Test
    void findAllIngredientsOmitsRowsWhoseInventoryItemIsMissing() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (1, 'Suppe', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");
        // foreign_key_checks is per-session, so the SET and the insert it protects must share one connection.
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute("SET foreign_key_checks = 0");
            st.execute("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (999, 1, 'pcs', 1)");
            st.execute("SET foreign_key_checks = 1");   // the connection goes back into the pool
        }

        assertTrue(service.findAllIngredients(1).isEmpty(),
                "the INNER JOIN drops ingredient rows that have no matching inventory_item, rather than erroring");
    }

    @Test
    void findAllInstructionsReturnsStepsOrderedByStepNumber() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (1, 'Suppe', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");
        exec("INSERT INTO recipe_instructions (recipe_instructions_step_id, recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES (1, 1, 2, 'Simmer for 10 minutes', NULL, 'Oma')");
        exec("INSERT INTO recipe_instructions (recipe_instructions_step_id, recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES (2, 1, 1, 'Chop the onions', 'fine dice', 'Oma')");

        List<RecipeInstruction> instructions = service.findAllInstructions(1);

        assertEquals(2, instructions.size());
        assertEquals(1, instructions.get(0).getStepNumber(), "steps must come back ordered by step number");
        assertEquals("Chop the onions", instructions.get(0).getInstruction());
        assertEquals("fine dice", instructions.get(0).getNotes());
        assertEquals(2, instructions.get(1).getStepNumber());
        assertNull(instructions.get(1).getNotes());
        assertEquals("Oma", instructions.get(1).getSource());
    }

    @Test
    void findAllInstructionsReturnsEmptyListForARecipeWithNoSteps() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (1, 'Suppe', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");

        assertTrue(service.findAllInstructions(1).isEmpty());
    }

    private void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

}
