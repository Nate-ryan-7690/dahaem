package de.dahaem.shopping;

import java.time.LocalDate;
import java.util.List;

/**
 * A previously generated shopping_list row, reloaded together with its persisted items. shoppingDate is the day of the
 * trip the list was made for and fromDate the first meal day it counts (both null on a list made before they existed).
 */
public record PersistedShoppingList(
        int id,
        Integer mealPlanId,
        LocalDate createdDate,
        LocalDate shoppingDate,
        LocalDate fromDate,
        boolean status,
        List<PersistedShoppingListItem> items
) {
    public PersistedShoppingList {
        items = List.copyOf(items);
    }
}
