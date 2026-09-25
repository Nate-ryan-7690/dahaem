package de.dahaem.api;

import de.dahaem.shopping.ShoppingList;
import de.dahaem.shopping.ShoppingListItem;
import de.dahaem.shopping.StoreOption;

import java.time.LocalDate;
import java.util.List;

/** A shopping list built from recipes: one line per (item, unit), each with the cheapest store option if any. */
public record ShoppingListDto(List<Line> items) {

    public record Line(int inventoryItemId, String nameDeutsch, double totalQuantity, String unit,
                       List<Integer> sourceRecipeIds, BestOption bestOption) {

        static Line from(ShoppingListItem item) {
            return new Line(item.inventoryItemId(), item.nameDeutsch(), item.totalQuantity(), item.unit(),
                    item.sourceRecipeIds(), BestOption.from(item.bestOption()));
        }
    }

    /** Where to buy one line and what it costs at the till (sale/deal price where one applies, plus deposit). */
    public record BestOption(String storeName, String brandName, String packageUnit, int packageSize,
                             Double normalPrice, boolean onSale, Double salePrice, int dealQuantity,
                             Double dealPrice, LocalDate validUntil, Double deposit, Double tillPrice) {

        static BestOption from(StoreOption option) {
            if (option == null) {
                return null;
            }
            return new BestOption(option.storeName(), option.brandName(), option.packageUnit(),
                    option.packageSize(), option.normalPrice(), option.onSale(), option.salePrice(),
                    option.dealQuantity(), option.dealPrice(), option.validUntil(), option.deposit(),
                    option.tillPrice());
        }
    }

    static ShoppingListDto from(ShoppingList list) {
        return new ShoppingListDto(list.items().stream().map(Line::from).toList());
    }
}
