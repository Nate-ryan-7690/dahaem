package de.dahaem.mealplanner;

import java.util.ArrayList;
import java.util.List;

/**
 * What to look for in the recipe list; plain data, built and checked by {@link #of}. Every filter that is set must
 * hold (AND):
 * <ul>
 *   <li>{@code nameWords}: each word is contained in the recipe name;</li>
 *   <li>{@code ingredients}: for each text, the recipe has an ingredient whose German or English name contains it;</li>
 *   <li>{@code tags}: the recipe carries all these tags (by name);</li>
 *   <li>{@code minTime} / {@code maxTime}: total time (prep + cook) in minutes; a recipe without a time (0) is
 *       left out as soon as either is set;</li>
 *   <li>{@code servings}: the recipe is for this many people (a "3 - 4 Personen" recipe matches 3 and 4).</li>
 * </ul>
 */
public record RecipeQuery(List<String> nameWords, List<String> ingredients, List<String> tags,
                          Integer minTime, Integer maxTime, Integer servings, RecipeSort sort) {

    /** At most this many words / ingredients / tags per request (keeps the SQL small). */
    public static final int MAX_TERMS = 10;

    public static RecipeQuery none() {
        return new RecipeQuery(List.of(), List.of(), List.of(), null, null, null, RecipeSort.NAME);
    }

    /** Cleans and checks raw request values; a bad value is an IllegalArgumentException (the API answers 400). */
    public static RecipeQuery of(String name, List<String> ingredients, List<String> tags, Integer minTime,
                                 Integer maxTime, Integer servings, String sort) {
        if (minTime != null && minTime < 0) {
            throw new IllegalArgumentException("'minTime' must be 0 or more (minutes)");
        }
        if (maxTime != null && maxTime < 0) {
            throw new IllegalArgumentException("'maxTime' must be 0 or more (minutes)");
        }
        if (minTime != null && maxTime != null && minTime > maxTime) {
            throw new IllegalArgumentException("'minTime' must not be larger than 'maxTime'");
        }
        if (servings != null && servings < 1) {
            throw new IllegalArgumentException("'servings' must be 1 or more");
        }
        return new RecipeQuery(limited("q", words(name)), limited("ingredient", clean(ingredients)),
                limited("tags", clean(tags)), minTime, maxTime, servings, RecipeSort.parse(sort));
    }

    /** True if no filter is set (the sort does not filter). */
    public boolean isUnfiltered() {
        return nameWords.isEmpty() && ingredients.isEmpty() && tags.isEmpty()
                && minTime == null && maxTime == null && servings == null;
    }

    /** True if a time filter is set. */
    public boolean hasTimeFilter() {
        return minTime != null || maxTime != null;
    }

    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text != null) {
            for (String w : text.trim().split("\\s+")) {
                if (!w.isEmpty()) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    private static List<String> clean(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values != null) {
            for (String v : values) {
                for (String part : v.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty() && !out.contains(t)) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    private static List<String> limited(String parameter, List<String> values) {
        if (values.size() > MAX_TERMS) {
            throw new IllegalArgumentException("'" + parameter + "' takes at most " + MAX_TERMS + " values");
        }
        return List.copyOf(values);
    }
}
