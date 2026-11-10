package de.dahaem.shopping;

import java.util.List;

/**
 * One persisted shopping_list_item line: the inventory item it is for (null only for a purely manual line with no
 * catalog match), its German/English name, how much is still needed and in which unit, the recipes that need it, every
 * current store option for it (the same options a freshly built line would show, cheapest till-price first, none
 * pre-chosen), and the store_item the user has selected so far, if any.
 *
 * <p>available is false when no store currently sells the item: the line is kept so the list is never silently
 * shorter than the plan. needsReview is true when stock exists but its unit differs from the line's, so nothing was
 * subtracted and a person checks the line; purchased is the tick "bought" on the shopping trip; stockQuantity / stockUnit show what the household has.
 */
public record PersistedShoppingListItem(
        int shoppingListItemId,
        Integer inventoryItemId,
        String nameDeutsch,
        String nameEnglish,
        double quantityNeeded,
        String unit,
        boolean available,
        boolean needsReview,
        boolean purchased,
        Integer stockQuantity,
        String stockUnit,
        boolean isManual,
        List<SourceRecipe> recipes,
        List<StoreOption> storeOptions,
        Integer selectedStoreItemId
) {
    public PersistedShoppingListItem {
        recipes = List.copyOf(recipes);
        storeOptions = List.copyOf(storeOptions);
    }
}
