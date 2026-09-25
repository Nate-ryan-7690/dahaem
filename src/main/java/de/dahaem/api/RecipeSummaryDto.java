package de.dahaem.api;

import de.dahaem.mealplanner.Recipe;

/** A recipe in a list: identity and the numbers you'd scan a list by. */
public record RecipeSummaryDto(int id, String name, int servings, int prepTime, int cookTime, Integer calories) {

    static RecipeSummaryDto from(Recipe recipe) {
        return new RecipeSummaryDto(recipe.getId(), recipe.getName(), recipe.getServings(),
                recipe.getPrepTime(), recipe.getCookTime(), recipe.getCalories());
    }
}
