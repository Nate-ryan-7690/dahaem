package de.dahaem.mealplanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The servings rule of a planned meal: the meal's own number, then the recipe's preference, then the household, then 2. */
class MealServingsTest {

    @Test
    void theMealsOwnNumberWinsOverEverything() {
        assertEquals(new MealServings.Resolved(5, "meal"), MealServings.resolve(5, 4, 3));
    }

    @Test
    void theRecipePreferenceWinsOverTheHousehold() {
        assertEquals(new MealServings.Resolved(4, "recipe"), MealServings.resolve(null, 4, 3));
    }

    @Test
    void theHouseholdHeadCountIsNextAndTwoIsTheLastResort() {
        assertEquals(new MealServings.Resolved(3, "household"), MealServings.resolve(null, null, 3));
        assertEquals(new MealServings.Resolved(2, "default"), MealServings.resolve(null, null, null));
    }

    @Test
    void aPreferenceAboveOrBelowTheHouseholdIsKept() {
        assertEquals(new MealServings.Resolved(1, "recipe"), MealServings.resolve(null, 1, 6));
        assertEquals(new MealServings.Resolved(8, "recipe"), MealServings.resolve(null, 8, 2));
    }

    @Test
    void aValueBelowOneCountsAsNotSet() {
        assertEquals(new MealServings.Resolved(2, "default"), MealServings.resolve(0, -1, 0));
        assertEquals(new MealServings.Resolved(3, "household"), MealServings.resolve(0, null, 3));
    }

    @Test
    void aPlannedMealResolvesFromItsOwnFields() {
        PlannedMeal followsRecipe = new PlannedMeal(1, 1, 1, "Suppe", null, java.time.LocalDate.of(2026, 11, 9),
                null, null, 6, 0, null);
        assertEquals(new MealServings.Resolved(6, "recipe"), MealServings.resolve(followsRecipe, 3));
    }
}
