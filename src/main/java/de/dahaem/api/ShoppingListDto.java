package de.dahaem.api;

import de.dahaem.shopping.ShoppingList;
import de.dahaem.shopping.ShoppingListItem;
import de.dahaem.shopping.StoreOption;

import java.time.LocalDate;
import java.util.List;

/**
 * A shopping list built from recipes: one line per (item, unit), with every current store's option
 * for it (cheapest till price first, but not pre-picked) and the store item the user has selected,
 * if any. DAHÄM surfaces the options; it never chooses one for the user.
 */
public record ShoppingListDto(List<Line> items) {

    public record Line(int inventoryItemId, String nameDeutsch, double totalQuantity, String unit,
                       List<Integer> sourceRecipeIds, List<StoreOptionDto> storeOptions,
                       Integer selectedStoreItemId) {

        static Line from(ShoppingListItem item) {
            return new Line(item.inventoryItemId(), item.nameDeutsch(), item.totalQuantity(), item.unit(),
                    item.sourceRecipeIds(), item.storeOptions().stream().map(StoreOptionDto::from).toList(),
                    item.selectedStoreItemId());
        }
    }

    /**
     * One store's current price for a line: what it costs at the till (sale/deal price where one
     * applies, plus deposit), plus whether this pack's unit is even comparable to what's needed.
     * unitCompatible is false when the pack's unit is a different physical dimension (or either
     * unit is unrecognized) than the line needs; packsNeeded/packsTotalPrice are only ever set
     * when it's true — never a fabricated pack count for an incompatible unit.
     */
    public record StoreOptionDto(int storeItemId, String storeName, String brandName, String packageUnit,
                             int packageSize, Double normalPrice, boolean onSale, Double salePrice,
                             int dealQuantity, Double dealPrice, LocalDate validUntil, Double deposit,
                             Double tillPrice, boolean unitCompatible, Integer packsNeeded, Double packsTotalPrice) {

        static StoreOptionDto from(StoreOption option) {
            return new StoreOptionDto(option.storeItemId(), option.storeName(), option.brandName(),
                    option.packageUnit(), option.packageSize(), option.normalPrice(), option.onSale(),
                    option.salePrice(), option.dealQuantity(), option.dealPrice(), option.validUntil(),
                    option.deposit(), option.tillPrice(), option.unitCompatible(), option.packsNeeded(),
                    option.packsTotalPrice());
        }
    }

    static ShoppingListDto from(ShoppingList list) {
        return new ShoppingListDto(list.items().stream().map(Line::from).toList());
    }
}
