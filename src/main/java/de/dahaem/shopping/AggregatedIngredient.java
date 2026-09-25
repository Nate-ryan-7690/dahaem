package de.dahaem.shopping;

import java.util.List;

/**
 * One inventory item's combined need across a set of recipes, before any store matching.
 * Quantities are only summed within the same unit, so the same item can appear as more
 * than one AggregatedIngredient if recipes ask for it in different units.
 */
public record AggregatedIngredient(
        int inventoryItemId,
        String nameDeutsch,
        double totalQuantity,
        String unit,
        List<Integer> sourceRecipeIds
) {
    public AggregatedIngredient {
        sourceRecipeIds = List.copyOf(sourceRecipeIds);
    }
}
