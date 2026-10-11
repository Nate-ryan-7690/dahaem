package de.dahaem.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single definition of portion scaling: a recipe written for {@code servings} people is
 * scaled to {@code people} by the factor {@code people / servings}. Used by the recipe detail
 * endpoint; the shopping list applies the same rule inside its aggregation SQL
 * (ShoppingListRepository.aggregateIngredients), always from {@code servings} (a range's lower
 * bound, see {@link #scalingServings}), with the same guards — people must be at
 * least 1, and a recipe whose servings is not positive is not scaled. "To taste" ingredients have
 * no quantity and are never scaled (nor put on a shopping list).
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

    /** Message for a {@code basis} value other than {@code servings} or {@code max}. */
    public static final String INVALID_BASIS_MESSAGE = "'basis' must be 'servings' or 'max'";

    /**
     * The servings count to scale a recipe from. A "3 – 4 Personen" recipe stores servings = 3 (the
     * lower bound, the default) and servingsMax = 4; with {@code useMax} it scales from 4 instead.
     * {@code useMax} is ignored when the recipe is not a range ({@code servingsMax} null).
     */
    public static int scalingServings(int servings, Integer servingsMax, boolean useMax) {
        return useMax && servingsMax != null ? servingsMax : servings;
    }

    /** Whether a recipe with this many servings can be scaled at all (servings must be positive). */
    public static boolean isScalable(int servings) {
        return servings > 0;
    }

    /**
     * {@code quantity * people / servings} on exact decimals (the stored DECIMAL(10,3) amount),
     * multiplied before dividing exactly as the shopping list SQL does, and rounded half-up to
     * 2 decimals once, at the end (0.5 Stück at 4 people of 2 → 1.00; 1 Stück at 3 of 2 → 1.50).
     * Returns {@code quantity} unchanged (not rounded) when the recipe is not scalable.
     *
     * @throws IllegalArgumentException if {@code people} is below 1
     */
    public static BigDecimal scaleRounded(BigDecimal quantity, int people, int servings) {
        if (!isValidPeople(people)) {
            throw new IllegalArgumentException(INVALID_PEOPLE_MESSAGE);
        }
        if (!isScalable(servings)) {
            return quantity;
        }
        return quantity.multiply(BigDecimal.valueOf(people))
                .divide(BigDecimal.valueOf(servings), 2, RoundingMode.HALF_UP);
    }
}
