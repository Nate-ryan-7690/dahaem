package de.dahaem.api;

import de.dahaem.mealplanner.MealPlanService;
import de.dahaem.shopping.ShoppingListService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shopping-lists")
public class ShoppingListItemController {

    private final ShoppingListService shoppingListService;
    private final MealPlanService mealPlanService;

    public ShoppingListItemController(ShoppingListService shoppingListService, MealPlanService mealPlanService) {
        this.shoppingListService = shoppingListService;
        this.mealPlanService = mealPlanService;
    }

    /** Every saved list, newest first, with its lines and how many are ticked; {@code mealPlanId} is null for a list without a plan. */
    @GetMapping
    public List<PersistedShoppingListDto.Summary> all() {
        return shoppingListService.allLists().stream().map(PersistedShoppingListDto.Summary::from).toList();
    }

    /**
     * Makes an empty list that belongs to no meal plan, for a trip without planned meals; the lines are added by hand
     * ({@code POST /{listId}/items}). {@code ?shoppingDate=YYYY-MM-DD} is the day of the trip (default today, not in the past).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GeneratedShoppingListDto create(@RequestParam(name = "shoppingDate", required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shoppingDate) {
        LocalDate today = mealPlanService.today();   // the household's day, not the server's
        return new GeneratedShoppingListDto(shoppingListService.createEmptyList(today,
                shoppingDate != null ? shoppingDate : today));
    }

    /** Reloads a previously generated/persisted shopping list, with each item's names, options, and selection. */
    @GetMapping("/{listId}")
    public PersistedShoppingListDto get(@PathVariable("listId") int listId) {
        return shoppingListService.loadPersistedList(listId)
                .map(PersistedShoppingListDto::from)
                .orElseThrow(() -> new NotFoundException("No shopping list with id " + listId));
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
        requireItemOnList(listId, itemId);
        shoppingListService.requireOpen(listId);
        shoppingListService.selectStoreItem(itemId, request.storeItemId());
        return new ShoppingListItemSelectionDto(itemId, request.storeItemId());
    }

    /** Ticks a line as bought on the shopping trip ({"purchased": true}) or unticks it. Stock is not changed. */
    @PutMapping("/{listId}/items/{itemId}/purchased")
    public ShoppingListEdits.PurchasedDto setPurchased(@PathVariable("listId") int listId,
                                                       @PathVariable("itemId") int itemId,
                                                       @RequestBody ShoppingListEdits.PurchasedRequest request) {
        if (request.purchased() == null) {
            throw new BadRequestException("'purchased' must be true or false");
        }
        requireItemOnList(listId, itemId);
        shoppingListService.setPurchased(listId, itemId, request.purchased());
        return new ShoppingListEdits.PurchasedDto(itemId, request.purchased());
    }

    /** Changes the amount of a line ({"quantity": 2.5}, above 0); the unit stays. */
    @PutMapping("/{listId}/items/{itemId}/quantity")
    public ShoppingListEdits.QuantityDto setQuantity(@PathVariable("listId") int listId,
                                                     @PathVariable("itemId") int itemId,
                                                     @RequestBody ShoppingListEdits.QuantityRequest request) {
        if (request.quantity() == null) {
            throw new BadRequestException("'quantity' is required");
        }
        requireItemOnList(listId, itemId);
        shoppingListService.setQuantity(listId, itemId, request.quantity());
        return new ShoppingListEdits.QuantityDto(itemId, request.quantity());
    }

    /** Removes a line from a list that is not completed. */
    @DeleteMapping("/{listId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable("listId") int listId, @PathVariable("itemId") int itemId) {
        requireItemOnList(listId, itemId);
        shoppingListService.removeItem(listId, itemId);
    }

    /** Adds a line by hand for a catalogue item ({"inventoryItemId": 5, "quantity": 2, "unit": "kg"}, unit optional). */
    @PostMapping("/{listId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListEdits.AddedLineDto addItem(@PathVariable("listId") int listId,
                                                  @RequestBody ShoppingListEdits.AddLineRequest request) {
        if (request.inventoryItemId() == null || request.quantity() == null) {
            throw new BadRequestException("'inventoryItemId' and 'quantity' are required");
        }
        if (shoppingListService.loadPersistedList(listId).isEmpty()) {
            throw new NotFoundException("No shopping list with id " + listId);
        }
        return new ShoppingListEdits.AddedLineDto(shoppingListService.addManualItem(listId,
                request.inventoryItemId(), request.quantity(), request.unit()));
    }

    /** Completes a list ({"completed": true}: it becomes read-only) or reopens it. */
    @PutMapping("/{listId}/status")
    public ShoppingListEdits.StatusDto setStatus(@PathVariable("listId") int listId,
                                                 @RequestBody ShoppingListEdits.StatusRequest request) {
        if (request.completed() == null) {
            throw new BadRequestException("'completed' must be true or false");
        }
        if (shoppingListService.loadPersistedList(listId).isEmpty()) {
            throw new NotFoundException("No shopping list with id " + listId);
        }
        shoppingListService.setCompleted(listId, request.completed());
        return new ShoppingListEdits.StatusDto(listId, request.completed());
    }

    private void requireItemOnList(int listId, int itemId) {
        int actualListId = shoppingListService.findShoppingListIdForItem(itemId)
                .orElseThrow(() -> new NotFoundException("No shopping list item with id " + itemId));
        if (actualListId != listId) {
            throw new NotFoundException("No shopping list item " + itemId + " on shopping list " + listId);
        }
    }
}
