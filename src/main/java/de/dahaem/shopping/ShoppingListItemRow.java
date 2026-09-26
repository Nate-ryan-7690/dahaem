package de.dahaem.shopping;

/**
 * Raw shopping_list_item row, joined to its inventory_item's names and unit. inventoryItemId,
 * nameDeutsch, nameEnglish and unit are all null for a purely manual line with no catalog match.
 * unit is the inventory_item's own tracking unit (inventory_item_unit) — quantityNeeded was
 * computed against that unit at generation time, so it doubles as the line's needed unit for
 * unit-aware pricing.
 */
public record ShoppingListItemRow(
        int itemId,
        Integer inventoryItemId,
        String nameDeutsch,
        String nameEnglish,
        String unit,
        int quantityNeeded,
        Integer selectedStoreItemId,
        boolean isManual
) {
}
