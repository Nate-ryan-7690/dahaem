package de.dahaem.mealplanner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeQueryTest {

    private static RecipeQuery of(String q, List<String> ingredients, List<String> tags, Integer min, Integer max,
                                  Integer servings, String sort) {
        return RecipeQuery.of(q, ingredients, tags, min, max, servings, sort);
    }

    @Test
    void nothingGivenIsUnfilteredAndSortedByName() {
        RecipeQuery q = of(null, null, null, null, null, null, null);
        assertTrue(q.isUnfiltered());
        assertEquals(RecipeSort.NAME, q.sort());
        assertFalse(q.hasTimeFilter());
    }

    @Test
    void blankSearchTextIsNoFilter() {
        assertTrue(of("   ", List.of(" ", ","), List.of(""), null, null, null, "").isUnfiltered());
    }

    @Test
    void nameIsSplitIntoWordsAndListsIntoTrimmedDistinctValues() {
        RecipeQuery q = of("  Gemüse   pfanne ", List.of("Möhre, Zwiebel", "Möhre"), List.of("vegan,schnell", "vegan"),
                null, null, null, null);
        assertEquals(List.of("Gemüse", "pfanne"), q.nameWords());
        assertEquals(List.of("Möhre", "Zwiebel"), q.ingredients());
        assertEquals(List.of("vegan", "schnell"), q.tags());
        assertFalse(q.isUnfiltered());
    }

    @Test
    void timeFiltersMustBeSensible() {
        assertTrue(of(null, null, null, 0, 30, null, null).hasTimeFilter());
        assertThrows(IllegalArgumentException.class, () -> of(null, null, null, -1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> of(null, null, null, null, -5, null, null));
        assertThrows(IllegalArgumentException.class, () -> of(null, null, null, 31, 30, null, null));
        assertEquals(30, of(null, null, null, 30, 30, null, null).maxTime());
    }

    @Test
    void servingsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> of(null, null, null, null, null, 0, null));
        assertEquals(4, of(null, null, null, null, null, 4, null).servings());
    }

    @Test
    void sortIsCaseInsensitiveAndUnknownIsRejected() {
        assertEquals(RecipeSort.TIME, of(null, null, null, null, null, null, "Time").sort());
        assertEquals(RecipeSort.NEWEST, of(null, null, null, null, null, null, " newest ").sort());
        assertThrows(IllegalArgumentException.class, () -> of(null, null, null, null, null, null, "name; DROP TABLE recipe"));
    }

    @Test
    void everySortEndsWithTheIdSoPagingIsStable() {
        for (RecipeSort sort : RecipeSort.values()) {
            assertTrue(sort.orderBy().endsWith("r.recipe_id"), sort.name());
        }
    }

    @Test
    void tooManyTermsAreRejected() {
        List<String> eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> "t" + i).toList();
        assertThrows(IllegalArgumentException.class, () -> of(null, null, eleven, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> of(null, eleven, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> of(String.join(" ", eleven), null, null, null, null, null, null));
    }
}
