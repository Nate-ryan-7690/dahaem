package de.dahaem.api;

import de.dahaem.shopping.ShoppingListService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shopping-lists")
public class ShoppingListItemController {

    private final ShoppingListService shoppingListService;

    public ShoppingListItemController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    /**
     * Sets which store product the user picked for a persisted line ({"storeItemId": 5}), or clears
     * the pick ({"storeItemId": null}). 404 if the item doesn't exist or isn't on this list; an
     * unknown storeItemId violates the store_item foreign key and comes back as 409.
     */
    @PutMapping("/{listId}/items/{itemId}/store-item")
    public ShoppingListItemSelectionDto selectStoreItem(@PathVariable("listId") int listId,
                                                          @PathVariable("itemId") int itemId,
                                                          @RequestBody SelectStoreItemRequest request) {
        int actualListId = shoppingListService.findShoppingListIdForItem(itemId)
                .orElseThrow(() -> new NotFoundException("No shopping list item with id " + itemId));
        if (actualListId != listId) {
            throw new NotFoundException("No shopping list item " + itemId + " on shopping list " + listId);
        }
        shoppingListService.selectStoreItem(itemId, request.storeItemId());
        return new ShoppingListItemSelectionDto(itemId, request.storeItemId());
    }
}
