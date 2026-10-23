package de.dahaem.mealplanner;

import java.util.Locale;

/**
 * The sort orders of the recipe list. Every order ends with the recipe id, so the order is total and paging never
 * repeats or skips a recipe. {@link #orderBy()} is a fixed fragment (never user text); the repository appends it.
 */
public enum RecipeSort {
    /** Name A-Z (the default). */
    NAME("r.recipe_name, r.recipe_id"),
    /** Shortest total time (prep + cook) first; recipes without a time last. */
    TIME("(r.recipe_prep_time + r.recipe_cook_time = 0), (r.recipe_prep_time + r.recipe_cook_time), "
            + "r.recipe_name, r.recipe_id"),
    /** Best rating first; unrated recipes last. */
    RATING("(r.recipe_rating_avg IS NULL), r.recipe_rating_avg DESC, r.recipe_rating_count DESC, "
            + "r.recipe_name, r.recipe_id"),
    /** Most recently published first; recipes without a date last. */
    NEWEST("(r.recipe_published_date IS NULL), r.recipe_published_date DESC, r.recipe_name, r.recipe_id");

    private final String orderBy;

    RecipeSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public String orderBy() {
        return orderBy;
    }

    /** The sort for a request value (case-insensitive, null / blank = name); unknown is an IllegalArgumentException. */
    public static RecipeSort parse(String value) {
        if (value == null || value.isBlank()) {
            return NAME;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sort '" + value + "'; known sorts: name, time, rating, newest");
        }
    }
}
