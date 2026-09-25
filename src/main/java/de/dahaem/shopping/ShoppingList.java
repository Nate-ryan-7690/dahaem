package de.dahaem.shopping;

import java.util.List;

/** A shopping list built from a set of recipes: one line per distinct (item, unit) need. */
public record ShoppingList(List<ShoppingListItem> items) {
    public ShoppingList {
        items = List.copyOf(items);
    }
}
