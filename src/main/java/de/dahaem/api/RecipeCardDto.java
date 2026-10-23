package de.dahaem.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dahaem.mealplanner.Recipe;

import java.math.BigDecimal;
import java.util.List;

/**
 * A recipe as a card in the list: what a card shows, nothing more ({@code GET /api/recipes/{id}} has the rest:
 * description, nutrition, image source, publish date, ingredients, instructions). {@code totalTime} is
 * {@code prepTime + cookTime} in minutes (0 when the recipe has no time). {@code servings} is the number of people;
 * {@code servingsMax} is the upper bound of a "3 – 4 Personen" range ({@code servings} is then the lower bound),
 * null otherwise. {@code imageUrl} / {@code thumbnailUrl} are links the browser loads from their source (null when
 * the recipe has no image: show a fallback; use the thumbnail in lists). {@code cuisine} is one German name,
 * {@code ratingAvg} is 0 - 5 with 2 decimals; both null when unknown. {@code tags} are the recipe's tag names (no
 * group or source: the detail has those), sorted by group then name; empty when it has none.
 * {@code diet} is present only when diet rules were passed to {@code GET /api/recipes} (see {@link DietDto}).
 */
public record RecipeCardDto(int id, String name, String imageUrl, String thumbnailUrl, int prepTime, int cookTime,
                            int totalTime, int servings, Integer servingsMax, String cuisine, BigDecimal ratingAvg,
                            List<String> tags, @JsonInclude(JsonInclude.Include.NON_NULL) DietDto diet) {

    static RecipeCardDto from(Recipe recipe, List<String> tags, DietDto diet) {
        return new RecipeCardDto(recipe.getId(), recipe.getName(), recipe.getImageUrl(), recipe.getThumbnailUrl(),
                recipe.getPrepTime(), recipe.getCookTime(), recipe.getPrepTime() + recipe.getCookTime(),
                recipe.getServings(), recipe.getServingsMax(), recipe.getCuisine(), recipe.getRatingAvg(), tags, diet);
    }
}
