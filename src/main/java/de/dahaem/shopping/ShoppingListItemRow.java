package de.dahaem.shopping;

/**
 * Raw shopping_list_item row, joined to its inventory_item. inventoryItemId, nameDeutsch, nameEnglish, stockQuantity
 * and stockUnit are null for a purely manual line with no catalog match. {@code unit} is the unit of the amount: the
 * line's own unit, or for a line made before migration 0017 the inventory_item's tracking unit. stockQuantity /
 * stockUnit is what the household has now, in the item's own unit (shown when a line needs review).
 */
public record ShoppingListItemRow(
        int itemId,
        Integer inventoryItemId,
        String nameDeutsch,
        String nameEnglish,
        String unit,
        double quantityNeeded,
        Integer selectedStoreItemId,
        boolean isManual,
        boolean needsReview,
        boolean purchased,
        Integer stockQuantity,
        String stockUnit
) {
}
