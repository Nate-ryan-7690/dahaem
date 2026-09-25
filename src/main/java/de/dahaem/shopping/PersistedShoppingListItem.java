package de.dahaem.shopping;

import java.util.List;

/**
 * One persisted shopping_list_item line: the inventory item it is for (null only for a purely
 * manual line with no catalog match), its German/English name, how much is still needed, every
 * current store option for it (the same options a freshly built line would show, cheapest
 * till-price first, none pre-chosen), and the store_item the user has selected so far, if any.
 */
public record PersistedShoppingListItem(
        int shoppingListItemId,
        Integer inventoryItemId,
        String nameDeutsch,
        String nameEnglish,
        int quantityNeeded,
        boolean isManual,
        List<StoreOption> storeOptions,
        Integer selectedStoreItemId
) {
    public PersistedShoppingListItem {
        storeOptions = List.copyOf(storeOptions);
    }
}
