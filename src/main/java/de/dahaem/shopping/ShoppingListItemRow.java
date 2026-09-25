package de.dahaem.shopping;

/**
 * Raw shopping_list_item row, joined to its inventory_item's names. inventoryItemId,
 * nameDeutsch and nameEnglish are all null for a purely manual line with no catalog match.
 */
public record ShoppingListItemRow(
        int itemId,
        Integer inventoryItemId,
        String nameDeutsch,
        String nameEnglish,
        int quantityNeeded,
        Integer selectedStoreItemId,
        boolean isManual
) {
}
