package de.dahaem.shopping;

import java.util.List;

/**
 * One line of a shopping list: an inventory item, how much of it is needed in total across
 * the recipes it came from, and every store's current option for it (ordered cheapest-till-price
 * first, but not pre-chosen — DAHÄM surfaces the options and the user decides). selectedStoreItemId
 * is the store_item the user has picked for a persisted line, or null while it is still undecided.
 */
public record ShoppingListItem(
        int inventoryItemId,
        String nameDeutsch,
        double totalQuantity,
        String unit,
        List<Integer> sourceRecipeIds,
        List<StoreOption> storeOptions,
        Integer selectedStoreItemId
) {
    public ShoppingListItem {
        sourceRecipeIds = List.copyOf(sourceRecipeIds);
        storeOptions = List.copyOf(storeOptions);
    }

    /** A freshly built line, before any store item has been selected. */
    public ShoppingListItem(int inventoryItemId, String nameDeutsch, double totalQuantity, String unit,
                             List<Integer> sourceRecipeIds, List<StoreOption> storeOptions) {
        this(inventoryItemId, nameDeutsch, totalQuantity, unit, sourceRecipeIds, storeOptions, null);
    }
}
