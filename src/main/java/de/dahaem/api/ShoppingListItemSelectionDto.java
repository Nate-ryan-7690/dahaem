package de.dahaem.api;

/** Response of PUT /api/shopping-lists/{listId}/items/{itemId}/store-item: the line's new selection. */
public record ShoppingListItemSelectionDto(int shoppingListItemId, Integer storeItemId) {
}
