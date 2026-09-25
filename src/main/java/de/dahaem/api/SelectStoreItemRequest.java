package de.dahaem.api;

/**
 * Body of PUT /api/shopping-lists/{listId}/items/{itemId}/store-item, e.g. {@code {"storeItemId": 5}}.
 * A null (or omitted) storeItemId clears the line's selection.
 */
public record SelectStoreItemRequest(Integer storeItemId) {
}
