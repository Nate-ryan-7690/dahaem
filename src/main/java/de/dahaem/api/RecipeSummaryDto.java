package de.dahaem.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dahaem.mealplanner.Recipe;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A recipe in a list: identity and the numbers you'd scan a list by. {@code servingsMax} is the
 * upper bound of a "3 – 4 Personen" range ({@code servings} is then the lower bound), null otherwise.
 * The enrichment fields are null when the recipe has no such data: {@code imageUrl} / {@code thumbnailUrl}
 * are links (the browser loads the image from its source), {@code imageSource} says where the image
 * comes from ({@code hellofresh}, {@code own} or {@code generated}), {@code cuisine} is one German name,
 * {@code ratingAvg} is 0 - 5 with 2 decimals.
 * {@code tags} are the recipe's tag names (no group or source: {@code GET /api/recipes/{id}} has those),
 * sorted by group then name; empty when it has none.
 * {@code diet} is present only when diet rules were passed to {@code GET /api/recipes} (see {@link DietDto}).
 */
public record RecipeSummaryDto(int id, String name, int servings, Integer servingsMax, int prepTime, int cookTime,
                               Integer calories, String description, String imageUrl, String thumbnailUrl,
                               String imageSource, String cuisine, BigDecimal ratingAvg, Integer ratingCount,
                               LocalDate publishedDate, List<String> tags,
                               @JsonInclude(JsonInclude.Include.NON_NULL) DietDto diet) {

    static RecipeSummaryDto from(Recipe recipe, List<String> tags) {
        return from(recipe, tags, null);
    }

    static RecipeSummaryDto from(Recipe recipe, List<String> tags, DietDto diet) {
        return new RecipeSummaryDto(recipe.getId(), recipe.getName(), recipe.getServings(), recipe.getServingsMax(),
                recipe.getPrepTime(), recipe.getCookTime(), recipe.getCalories(), recipe.getDescription(),
                recipe.getImageUrl(), recipe.getThumbnailUrl(), recipe.getImageSource(), recipe.getCuisine(),
                recipe.getRatingAvg(), recipe.getRatingCount(), recipe.getPublishedDate(), tags, diet);
    }
}
