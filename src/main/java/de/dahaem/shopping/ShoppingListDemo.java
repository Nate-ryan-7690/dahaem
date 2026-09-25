package de.dahaem.shopping;

import java.util.List;

/**
 * Temporary console runner for the shopping-list slice, same idea as de.dahaem.Main:
 * proves the aggregation + store-matching logic works before there is a UI for it.
 */
public class ShoppingListDemo {
    public static void main(String[] args) {
        ShoppingListService service = new ShoppingListService(new ShoppingListRepository());

        List<Integer> recipeIds = List.of(1, 2);
        System.out.println("Building shopping list for recipes " + recipeIds + "...");
        ShoppingList list = service.buildFromRecipes(recipeIds);

        for (ShoppingListItem item : list.items()) {
            System.out.printf("  %.0f %s %s (from recipes %s)%n",
                    item.totalQuantity(), item.unit(), item.nameDeutsch(), item.sourceRecipeIds());
            StoreOption best = item.bestOption();
            if (best == null) {
                System.out.println("      no store option found");
                continue;
            }
            String priceInfo = best.onSale()
                    ? String.format("SALE %.2f EUR (normal %s)", best.salePrice(),
                            best.normalPrice() == null ? "unknown" : String.format("%.2f EUR", best.normalPrice()))
                    : String.format("%.2f EUR", best.normalPrice());
            System.out.printf("      best: %s, %s, %d %s, %s, till price %.2f EUR%s%n",
                    best.storeName(), best.brandName(), best.packageSize(), best.packageUnit(),
                    priceInfo, best.tillPrice(),
                    best.validUntil() != null ? " (valid until " + best.validUntil() + ")" : "");
        }
    }
}
