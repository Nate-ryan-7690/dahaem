package de.dahaem.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single definition of portion scaling: a recipe written for {@code servings} people is
 * scaled to {@code people} by the factor {@code people / servings}. Used by the recipe detail
 * endpoint; the shopping list applies the same rule inside its aggregation SQL
 * (ShoppingListRepository.aggregateIngredients), with the same guards — people must be at
 * least 1, and a recipe whose servings is not positive is not scaled.
 */
public final class Portions {

    private Portions() {
    }

    /** Message for a {@code people} value below 1, shared by every endpoint that takes {@code ?people=}. */
    public static final String INVALID_PEOPLE_MESSAGE = "'people' must be at least 1";

    /** Whether {@code people} is a usable head count (at least 1). */
    public static boolean isValidPeople(int people) {
        return people >= 1;
    }

    /** Whether a recipe with this many servings can be scaled at all (servings must be positive). */
    public static boolean isScalable(int servings) {
        return servings > 0;
    }

    /**
     * {@code quantity * people / servings}, multiplied before dividing exactly as the shopping list
     * SQL does. Returns {@code quantity} unchanged when the recipe is not scalable.
     *
     * @throws IllegalArgumentException if {@code people} is below 1
     */
    public static double scale(double quantity, int people, int servings) {
        if (!isValidPeople(people)) {
            throw new IllegalArgumentException(INVALID_PEOPLE_MESSAGE);
        }
        if (!isScalable(servings)) {
            return quantity;
        }
        return quantity * people / servings;
    }

    /** {@link #scale} rounded half-up to 2 decimals, for display (1 Stück at 3 people of 2 → 1.5). */
    public static double scaleRounded(double quantity, int people, int servings) {
        return BigDecimal.valueOf(scale(quantity, people, servings)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
