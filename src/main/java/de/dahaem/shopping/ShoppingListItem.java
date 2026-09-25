package de.dahaem.shopping;

import java.util.List;

/**
 * One line of a shopping list: an inventory item, how much of it is needed in total across
 * the recipes it came from, and (if any store carries it) the best place to buy it.
 */
public record ShoppingListItem(
        int inventoryItemId,
        String nameDeutsch,
        double totalQuantity,
        String unit,
        List<Integer> sourceRecipeIds,
        StoreOption bestOption
) {
    public ShoppingListItem {
        sourceRecipeIds = List.copyOf(sourceRecipeIds);
    }
}
