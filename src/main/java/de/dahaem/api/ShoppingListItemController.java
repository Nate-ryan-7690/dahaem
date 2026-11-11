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

    /**
     * Every saved list, newest first, with its lines and how many are ticked; {@code mealPlanId} is null for a list
     * without a plan. Archived lists are left out; {@code ?archived=true} shows only the archived ones.
     */
    @GetMapping
    public List<PersistedShoppingListDto.Summary> all(@RequestParam(name = "archived", defaultValue = "false") boolean archived,
                                                      Caller caller) {
        return shoppingListService.allLists(caller.householdId(), archived).stream().map(PersistedShoppingListDto.Summary::from).toList();
    }

    /**
     * Makes an empty list that belongs to no meal plan, for a trip without planned meals; the lines are added by hand
     * ({@code POST /{listId}/items}). {@code ?shoppingDate=YYYY-MM-DD} is the day of the trip (default today, not in the past).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GeneratedShoppingListDto create(@RequestParam(name = "shoppingDate", required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shoppingDate,
                                           Caller caller) {
        LocalDate today = mealPlanService.today();   // the household's day, not the server's
        return new GeneratedShoppingListDto(shoppingListService.createEmptyList(caller.householdId(), today,
                shoppingDate != null ? shoppingDate : today));
    }

    /** Reloads a previously generated/persisted shopping list, with each item's names, options, and selection. */
    @GetMapping("/{listId}")
    public PersistedShoppingListDto get(@PathVariable("listId") int listId, Caller caller) {
        requireOwn(listId, caller);
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
                                                          @RequestBody SelectStoreItemRequest request,
                                                          Caller caller) {
        requireOwn(listId, caller);
        requireItemOnList(listId, itemId);
        shoppingListService.requireOpen(listId);
        shoppingListService.selectStoreItem(itemId, request.storeItemId());
        return new ShoppingListItemSelectionDto(itemId, request.storeItemId());
    }

    /** Ticks a line as bought on the shopping trip ({"purchased": true}) or unticks it. Stock is not changed. */
    @PutMapping("/{listId}/items/{itemId}/purchased")
    public ShoppingListEdits.PurchasedDto setPurchased(@PathVariable("listId") int listId,
                                                       @PathVariable("itemId") int itemId,
                                                       @RequestBody ShoppingListEdits.PurchasedRequest request,
                                                       Caller caller) {
        requireOwn(listId, caller);
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
                                                     @RequestBody ShoppingListEdits.QuantityRequest request,
                                                     Caller caller) {
        requireOwn(listId, caller);
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
    public void removeItem(@PathVariable("listId") int listId, @PathVariable("itemId") int itemId, Caller caller) {
        requireOwn(listId, caller);
        requireItemOnList(listId, itemId);
        shoppingListService.removeItem(listId, itemId);
    }

    /**
     * Adds a line by hand: for a catalogue item ({"inventoryItemId": 5, "quantity": 2, "unit": "kg"}, unit optional) or as
     * free text ({"name": "Backpapier", "quantity": 1}, unit optional). Exactly one of inventoryItemId and name.
     */
    @PostMapping("/{listId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListEdits.AddedLineDto addItem(@PathVariable("listId") int listId,
                                                  @RequestBody ShoppingListEdits.AddLineRequest request,
                                                  Caller caller) {
        requireOwn(listId, caller);
        if (request.quantity() == null) {
            throw new BadRequestException("'quantity' is required");
        }
        if (shoppingListService.loadPersistedList(listId).isEmpty()) {
            throw new NotFoundException("No shopping list with id " + listId);
        }
        return new ShoppingListEdits.AddedLineDto(shoppingListService.addManualItem(listId,
                request.inventoryItemId(), request.name(), request.quantity(), request.unit()));
    }

    /** Archives a list ({"archived": true}: hidden from the overview, nothing is deleted) or brings it back. */
    @PutMapping("/{listId}/archived")
    public ShoppingListEdits.ArchivedDto setArchived(@PathVariable("listId") int listId,
                                                     @RequestBody ShoppingListEdits.ArchivedRequest request,
                                                     Caller caller) {
        requireOwn(listId, caller);
        if (request.archived() == null) {
            throw new BadRequestException("'archived' must be true or false");
        }
        if (shoppingListService.loadPersistedList(listId).isEmpty()) {
            throw new NotFoundException("No shopping list with id " + listId);
        }
        shoppingListService.setArchived(listId, request.archived());
        return new ShoppingListEdits.ArchivedDto(listId, request.archived());
    }

    /** Completes a list ({"completed": true}: it becomes read-only) or reopens it. */
    @PutMapping("/{listId}/status")
    public ShoppingListEdits.StatusDto setStatus(@PathVariable("listId") int listId,
                                                 @RequestBody ShoppingListEdits.StatusRequest request,
                                                 Caller caller) {
        requireOwn(listId, caller);
        if (request.completed() == null) {
            throw new BadRequestException("'completed' must be true or false");
        }
        if (shoppingListService.loadPersistedList(listId).isEmpty()) {
            throw new NotFoundException("No shopping list with id " + listId);
        }
        shoppingListService.setCompleted(listId, request.completed());
        return new ShoppingListEdits.StatusDto(listId, request.completed());
    }

    /** A list of another household is not found, exactly like one that does not exist. */
    private void requireOwn(int listId, Caller caller) {
        if (!shoppingListService.isOwnedBy(listId, caller.householdId())) {
            throw new NotFoundException("No shopping list with id " + listId);
        }
    }

    private void requireItemOnList(int listId, int itemId) {
        int actualListId = shoppingListService.findShoppingListIdForItem(itemId)
                .orElseThrow(() -> new NotFoundException("No shopping list item with id " + itemId));
        if (actualListId != listId) {
            throw new NotFoundException("No shopping list item " + itemId + " on shopping list " + listId);
        }
    }
}
