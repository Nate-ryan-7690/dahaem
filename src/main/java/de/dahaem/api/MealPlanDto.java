package de.dahaem.api;

import de.dahaem.mealplanner.MealPlan;
import de.dahaem.mealplanner.MealPlanRecipe;

import java.time.LocalDate;
import java.util.List;

/** One weekly meal plan with the recipes slotted into it. */
public record MealPlanDto(int id, LocalDate weekStartDate, List<PlannedRecipe> recipes) {

    public record PlannedRecipe(int recipeId, String recipeName, String mealTime) {

        static PlannedRecipe from(MealPlanRecipe recipe) {
            return new PlannedRecipe(recipe.getRecipeId(), recipe.getRecipeName(), recipe.getMealTime());
        }
    }

    static MealPlanDto from(MealPlan plan, List<MealPlanRecipe> recipes) {
        return new MealPlanDto(plan.getId(), plan.getWeekStartDate(),
                recipes.stream().map(PlannedRecipe::from).toList());
    }
}
