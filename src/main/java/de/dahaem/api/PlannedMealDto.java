package de.dahaem.api;

import de.dahaem.mealplanner.MealServings;
import de.dahaem.mealplanner.PlannedMeal;

import java.time.LocalDate;

/**
 * One planned meal. {@code servings} is the number the meal is cooked for (resolved, always at least 1) and
 * {@code servingsSource} says where it came from: {@code meal} (set on the meal itself), {@code recipe} (the
 * recipe's preferred servings), {@code household} (the household's head count) or {@code default} (2).
 * {@code ownServings} is only what was set on the meal (null = follows the default), so a page can tell a
 * hand-set number from a followed one. {@code recipePreferredServings} is the recipe's permanent preference.
 */
public record PlannedMealDto(int entryId, int planId, int recipeId, String recipeName, String recipeThumbnailUrl,
                             LocalDate date, String label, int position, String note,
                             int servings, String servingsSource, Integer ownServings,
                             Integer recipePreferredServings) {

    static PlannedMealDto from(PlannedMeal meal, Integer householdPeople) {
        MealServings.Resolved resolved = MealServings.resolve(meal, householdPeople);
        return new PlannedMealDto(meal.getEntryId(), meal.getPlanId(), meal.getRecipeId(), meal.getRecipeName(),
                meal.getRecipeThumbnailUrl(), meal.getDate(), meal.getLabel(), meal.getPosition(), meal.getNote(),
                resolved.servings(), resolved.source(), meal.getOwnServings(), meal.getRecipePreferredServings());
    }
}
