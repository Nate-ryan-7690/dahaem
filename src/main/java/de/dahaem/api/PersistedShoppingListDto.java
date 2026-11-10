package de.dahaem.api;

import de.dahaem.shopping.PersistedShoppingList;
import de.dahaem.shopping.PersistedShoppingListItem;
import de.dahaem.shopping.ShoppingListSummary;

import java.time.LocalDate;
import java.util.List;

/**
 * A previously generated shopping list as it was persisted: its items with names, the amount still needed and its
 * unit, every current store option for each item (cheapest till price first, but not pre-picked) and the store item
 * the user has selected, if any. DAHÄM surfaces the options; it never chooses one for the user.
 *
 * <p>{@code shoppingDate} is the day of the trip the list was made for: the offers in the store options are the ones
 * that run on that day ({@code validFrom} / {@code validUntil} on each option say when). {@code fromDate} is the first
 * meal day the list counts. Both are null on a list made before they existed.
 *
 * <p>Each line: {@code available} is false when no store currently sells the item (the line is kept, never silently
 * dropped); {@code needsReview} is true when stock exists but in another unit than the line's, so nothing was
 * subtracted and a person checks it ({@code stockQuantity} / {@code stockUnit} show what the household has);
 * {@code recipes} are the recipes that need the line.
 */
public record PersistedShoppingListDto(int id, Integer mealPlanId, LocalDate createdDate, LocalDate shoppingDate,
                                        LocalDate fromDate, boolean status, List<Line> items) {

    public record Line(int shoppingListItemId, Integer inventoryItemId, String nameDeutsch, String nameEnglish,
                        double quantityNeeded, String unit, boolean available, boolean needsReview,
                        Integer stockQuantity, String stockUnit, boolean isManual, List<RecipeRef> recipes,
                        List<ShoppingListDto.StoreOptionDto> storeOptions, Integer selectedStoreItemId) {
        static Line from(PersistedShoppingListItem item) {
            return new Line(item.shoppingListItemId(), item.inventoryItemId(), item.nameDeutsch(),
                    item.nameEnglish(), item.quantityNeeded(), item.unit(), item.available(), item.needsReview(),
                    item.stockQuantity(), item.stockUnit(), item.isManual(),
                    item.recipes().stream().map(r -> new RecipeRef(r.id(), r.name())).toList(),
                    item.storeOptions().stream().map(ShoppingListDto.StoreOptionDto::from).toList(),
                    item.selectedStoreItemId());
        }
    }

    /** A recipe that needs a line. */
    public record RecipeRef(int id, String name) {
    }

    /** One saved list of a meal plan in short (see {@code GET /api/meal-plans/{id}/shopping-lists}). */
    public record Summary(int id, LocalDate createdDate, LocalDate shoppingDate, LocalDate fromDate, boolean status,
                          int lines) {
        static Summary from(ShoppingListSummary s) {
            return new Summary(s.id(), s.createdDate(), s.shoppingDate(), s.fromDate(), s.status(), s.lines());
        }
    }

    static PersistedShoppingListDto from(PersistedShoppingList list) {
        return new PersistedShoppingListDto(list.id(), list.mealPlanId(), list.createdDate(), list.shoppingDate(),
                list.fromDate(), list.status(), list.items().stream().map(Line::from).toList());
    }
}
