package de.dahaem.api;

import de.dahaem.mealplanner.Recipe;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recipe in a list: identity and the numbers you'd scan a list by. {@code servingsMax} is the
 * upper bound of a "3 – 4 Personen" range ({@code servings} is then the lower bound), null otherwise.
 * The enrichment fields are null when the recipe has no such data: {@code imageUrl} / {@code thumbnailUrl}
 * are links (the browser loads the image from its source), {@code imageSource} says where the image
 * comes from ({@code hellofresh}, {@code own} or {@code generated}), {@code cuisine} is one German name,
 * {@code ratingAvg} is 0 - 5 with 2 decimals.
 */
public record RecipeSummaryDto(int id, String name, int servings, Integer servingsMax, int prepTime, int cookTime,
                               Integer calories, String description, String imageUrl, String thumbnailUrl,
                               String imageSource, String cuisine, BigDecimal ratingAvg, Integer ratingCount,
                               LocalDate publishedDate) {

    static RecipeSummaryDto from(Recipe recipe) {
        return new RecipeSummaryDto(recipe.getId(), recipe.getName(), recipe.getServings(), recipe.getServingsMax(),
                recipe.getPrepTime(), recipe.getCookTime(), recipe.getCalories(), recipe.getDescription(),
                recipe.getImageUrl(), recipe.getThumbnailUrl(), recipe.getImageSource(), recipe.getCuisine(),
                recipe.getRatingAvg(), recipe.getRatingCount(), recipe.getPublishedDate());
    }
}
