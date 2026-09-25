package de.dahaem.shopping;

import java.time.LocalDate;
import java.util.List;

/** A previously generated shopping_list row, reloaded together with its persisted items. */
public record PersistedShoppingList(
        int id,
        Integer mealPlanId,
        LocalDate createdDate,
        boolean status,
        List<PersistedShoppingListItem> items
) {
    public PersistedShoppingList {
        items = List.copyOf(items);
    }
}
