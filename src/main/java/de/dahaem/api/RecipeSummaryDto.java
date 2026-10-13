package de.dahaem.api;

import de.dahaem.mealplanner.Recipe;

import java.util.List;

/**
 * A recipe in a list: identity and the numbers you'd scan a list by. {@code servingsMax} is the
 * upper bound of a "3 – 4 Personen" range ({@code servings} is then the lower bound), null otherwise.
 * {@code tags} are the recipe's tag names (no group or source: {@code GET /api/recipes/{id}} has those),
 * sorted by group then name; empty when it has none.
 */
public record RecipeSummaryDto(int id, String name, int servings, Integer servingsMax, int prepTime, int cookTime,
                               Integer calories, List<String> tags) {

    static RecipeSummaryDto from(Recipe recipe, List<String> tags) {
        return new RecipeSummaryDto(recipe.getId(), recipe.getName(), recipe.getServings(), recipe.getServingsMax(),
                recipe.getPrepTime(), recipe.getCookTime(), recipe.getCalories(), tags);
    }
}
