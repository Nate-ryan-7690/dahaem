package de.dahaem.mealplanner;

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
 * Exercises the meal-planning slice against a real, throwaway MariaDB database built from the
 * subset of the schema it needs (recipe + meal_plan + meal_plan_recipe).
 * The repository under test is never mocked: only Database's target is swapped out.
 */
class MealPlanServiceTest {

    private MealPlanService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        service = new MealPlanService(new MealPlanRepository());
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void createsAWeeklyPlanAndReturnsItsGeneratedId() {
        int planId = service.createWeeklyPlan(LocalDate.of(2026, 9, 28));

        List<MealPlan> plans = service.allPlans();
        assertEquals(1, plans.size());
        assertEquals(planId, plans.get(0).getId());
        assertEquals(LocalDate.of(2026, 9, 28), plans.get(0).getWeekStartDate());
    }

    @Test
    void addsRecipesToAPlanAndListsThemWithNames() throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (1, 'Suppe', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (2, 'Eintopf', 4, 45, 15, NULL, NULL, NULL, NULL, NULL, NULL)");
        int planId = service.createWeeklyPlan(LocalDate.of(2026, 9, 28));

        service.addRecipe(planId, 1, "dinner");
        service.addRecipe(planId, 2, "lunch");

        List<MealPlanRecipe> recipes = service.recipesForPlan(planId);
        assertEquals(2, recipes.size());
        assertTrue(recipes.stream().anyMatch(r -> r.getRecipeId() == 1
                && r.getRecipeName().equals("Suppe") && r.getMealTime().equals("dinner")));
        assertTrue(recipes.stream().anyMatch(r -> r.getRecipeId() == 2
                && r.getRecipeName().equals("Eintopf") && r.getMealTime().equals("lunch")));
    }

    private void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

}
