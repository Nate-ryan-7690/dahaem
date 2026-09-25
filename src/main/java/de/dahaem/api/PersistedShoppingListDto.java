package de.dahaem.api;

import de.dahaem.shopping.PersistedShoppingList;
import de.dahaem.shopping.PersistedShoppingListItem;

import java.time.LocalDate;
import java.util.List;

/**
 * A previously generated shopping list as it was persisted: its items with names, quantity still
 * needed, every current store option for each item (cheapest till price first, but not pre-picked)
 * and the store item the user has selected, if any. DAHÄM surfaces the options; it never chooses
 * one for the user.
 */
public record PersistedShoppingListDto(int id, Integer mealPlanId, LocalDate createdDate, boolean status,
                                        List<Line> items) {

    public record Line(int shoppingListItemId, Integer inventoryItemId, String nameDeutsch, String nameEnglish,
                        int quantityNeeded, boolean isManual, List<ShoppingListDto.StoreOptionDto> storeOptions,
                        Integer selectedStoreItemId) {

        static Line from(PersistedShoppingListItem item) {
            return new Line(item.shoppingListItemId(), item.inventoryItemId(), item.nameDeutsch(),
                    item.nameEnglish(), item.quantityNeeded(), item.isManual(),
                    item.storeOptions().stream().map(ShoppingListDto.StoreOptionDto::from).toList(),
                    item.selectedStoreItemId());
        }
    }

    static PersistedShoppingListDto from(PersistedShoppingList list) {
        return new PersistedShoppingListDto(list.id(), list.mealPlanId(), list.createdDate(), list.status(),
                list.items().stream().map(Line::from).toList());
    }
}
