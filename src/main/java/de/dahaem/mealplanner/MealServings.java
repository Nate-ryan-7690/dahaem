package de.dahaem.mealplanner;

/**
 * The single definition of how many people a planned meal is cooked for. Most specific first:
 * the number set on the meal itself, then the recipe's permanent preference
 * ({@code recipe_preferred_servings}: "this meal is always for X"), then the household's head count,
 * then {@value #DEFAULT_SERVINGS}. No SQL here.
 */
public final class MealServings {

    /** Used when nothing else is set. */
    public static final int DEFAULT_SERVINGS = 2;

    public static final String SOURCE_MEAL = "meal";
    public static final String SOURCE_RECIPE = "recipe";
    public static final String SOURCE_HOUSEHOLD = "household";
    public static final String SOURCE_DEFAULT = "default";

    private MealServings() {
    }

    /** The resolved number and where it came from ({@code meal}, {@code recipe}, {@code household}, {@code default}). */
    public record Resolved(int servings, String source) {
    }

    /** Each argument may be null (not set); a value below 1 counts as not set, as the database refuses it anyway. */
    public static Resolved resolve(Integer own, Integer recipePreferred, Integer householdPeople) {
        if (usable(own)) {
            return new Resolved(own, SOURCE_MEAL);
        }
        if (usable(recipePreferred)) {
            return new Resolved(recipePreferred, SOURCE_RECIPE);
        }
        if (usable(householdPeople)) {
            return new Resolved(householdPeople, SOURCE_HOUSEHOLD);
        }
        return new Resolved(DEFAULT_SERVINGS, SOURCE_DEFAULT);
    }

    public static Resolved resolve(PlannedMeal meal, Integer householdPeople) {
        return resolve(meal.getOwnServings(), meal.getRecipePreferredServings(), householdPeople);
    }

    private static boolean usable(Integer value) {
        return value != null && value >= 1;
    }
}
