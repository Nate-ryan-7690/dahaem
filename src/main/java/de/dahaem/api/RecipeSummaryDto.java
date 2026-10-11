package de.dahaem.api;

import de.dahaem.mealplanner.Recipe;

/**
 * A recipe in a list: identity and the numbers you'd scan a list by. {@code servingsMax} is the
 * upper bound of a "3 – 4 Personen" range ({@code servings} is then the lower bound), null otherwise.
 */
public record RecipeSummaryDto(int id, String name, int servings, Integer servingsMax, int prepTime, int cookTime,
                               Integer calories) {

    static RecipeSummaryDto from(Recipe recipe) {
        return new RecipeSummaryDto(recipe.getId(), recipe.getName(), recipe.getServings(), recipe.getServingsMax(),
                recipe.getPrepTime(), recipe.getCookTime(), recipe.getCalories());
    }
}
