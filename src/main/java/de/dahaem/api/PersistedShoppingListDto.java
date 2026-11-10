package de.dahaem.api;

import de.dahaem.shopping.PersistedShoppingList;
import de.dahaem.shopping.PersistedShoppingListItem;
import de.dahaem.shopping.ShoppingListSummary;
import de.dahaem.shopping.StoreTotal;

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
 * {@code recipes} are the recipes that need the line; {@code purchased} is the tick "bought" on the trip.
 *
 * <p>{@code archived} true = hidden from the overview (nothing is deleted); a free-text line (added with a {@code name})
 * has no {@code inventoryItemId}, its text is in {@code nameDeutsch} and {@code nameEnglish}, and it has no store options.
 *
 * <p>{@code status} true = completed (read-only). {@code storeTotals}: for each store that sells something on the list,
 * how many of the lines still to buy it covers and what those lines cost there (the cheapest pack option per line);
 * shown side by side, never picked for the user.
 */
public record PersistedShoppingListDto(int id, Integer mealPlanId, LocalDate createdDate, LocalDate shoppingDate,
                                        LocalDate fromDate, boolean status, boolean archived,
                                        List<Line> items,
                                        List<StoreTotal> storeTotals) {

    public record Line(int shoppingListItemId, Integer inventoryItemId, String nameDeutsch, String nameEnglish,
                        double quantityNeeded, String unit, boolean available, boolean needsReview,
                        boolean purchased, Integer stockQuantity, String stockUnit, boolean isManual, List<RecipeRef> recipes,
                        List<ShoppingListDto.StoreOptionDto> storeOptions, Integer selectedStoreItemId) {
        static Line from(PersistedShoppingListItem item) {
            return new Line(item.shoppingListItemId(), item.inventoryItemId(), item.nameDeutsch(),
                    item.nameEnglish(), item.quantityNeeded(), item.unit(), item.available(), item.needsReview(),
                    item.purchased(), item.stockQuantity(), item.stockUnit(), item.isManual(),
                    item.recipes().stream().map(r -> new RecipeRef(r.id(), r.name())).toList(),
                    item.storeOptions().stream().map(ShoppingListDto.StoreOptionDto::from).toList(),
                    item.selectedStoreItemId());
        }
    }

    /** A recipe that needs a line. */
    public record RecipeRef(int id, String name) {
    }

    /** One saved list of a meal plan in short (see {@code GET /api/meal-plans/{id}/shopping-lists}); {@code purchasedLines} of its {@code lines} are ticked as bought. */
    public record Summary(int id, Integer mealPlanId, LocalDate createdDate, LocalDate shoppingDate, LocalDate fromDate, boolean status,
                          boolean archived,
                          int lines, int purchasedLines) {
        static Summary from(ShoppingListSummary s) {
            return new Summary(s.id(), s.mealPlanId(), s.createdDate(), s.shoppingDate(), s.fromDate(), s.status(), s.archived(), s.lines(), s.purchasedLines());
        }
    }

    static PersistedShoppingListDto from(PersistedShoppingList list) {
        return new PersistedShoppingListDto(list.id(), list.mealPlanId(), list.createdDate(), list.shoppingDate(),
                list.fromDate(), list.status(), list.archived(), list.items().stream().map(Line::from).toList(),
                StoreTotal.of(list.items()));
    }
}
