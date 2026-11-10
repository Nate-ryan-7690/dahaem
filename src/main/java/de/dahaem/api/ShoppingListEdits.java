package de.dahaem.api;

/** Request and response bodies of the shopping list edits (tick, amount, add a line, complete). */
public final class ShoppingListEdits {

    private ShoppingListEdits() {
    }

    /** PUT .../items/{itemId}/purchased: {@code {"purchased": true}}. */
    public record PurchasedRequest(Boolean purchased) {
    }

    /** PUT .../items/{itemId}/quantity: {@code {"quantity": 2.5}}; the line keeps its unit. */
    public record QuantityRequest(Double quantity) {
    }

    /**
     * POST .../items: a line added by hand, either for a catalogue item ({@code inventoryItemId}; {@code unit} defaults to
     * the item's own unit) or as free text ({@code name}, for things like "Backpapier"). Exactly one of the two.
     */
    public record AddLineRequest(Integer inventoryItemId, String name, Double quantity, String unit) {
    }

    /** PUT .../archived: {@code {"archived": true}} hides the list from the overview (nothing is deleted), false brings it back. */
    public record ArchivedRequest(Boolean archived) {
    }

    public record ArchivedDto(int id, boolean archived) {
    }

    /** PUT .../status: {@code {"completed": true}} makes the list read-only, false reopens it. */
    public record StatusRequest(Boolean completed) {
    }

    public record PurchasedDto(int shoppingListItemId, boolean purchased) {
    }

    public record QuantityDto(int shoppingListItemId, double quantity) {
    }

    public record AddedLineDto(int shoppingListItemId) {
    }

    public record StatusDto(int id, boolean completed) {
    }
}
