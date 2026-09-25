package de.dahaem.api;

/** Response of POST /api/meal-plans/{planId}/shopping-list: the id of the newly persisted list. */
public record GeneratedShoppingListDto(int shoppingListId) {
}
