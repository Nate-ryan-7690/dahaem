package de.dahaem.shopping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the lines still to buy would cost at one store, for the user to compare. It never picks a store: every store that
 * sells something on the list gets a row. {@code linesCovered} is how many of the {@code linesToBuy} lines the store has
 * with a known, believable pack price; {@code total} adds the cheapest such option of the store per covered line.
 * {@code linesNotCounted} are lines the store only has with an implausible pack count (see
 * {@link StoreOption#MAX_PLAUSIBLE_PACKS}: almost certainly a wrong pack size): they are NOT in the total. A line the
 * store does not cover is not in the total either, so compare the totals together with the coverage.
 */
public record StoreTotal(String storeName, int linesCovered, int linesNotCounted, int linesToBuy, double total) {

    /** One row per store, most lines covered first, then cheapest. Lines already ticked as bought are left out. */
    public static List<StoreTotal> of(List<PersistedShoppingListItem> items) {
        List<PersistedShoppingListItem> toBuy = items.stream().filter(i -> !i.purchased()).toList();
        Map<String, double[]> perStore = new LinkedHashMap<>();   // store -> {lines covered, total, lines not counted}
        for (PersistedShoppingListItem item : toBuy) {
            Map<String, Double> cheapest = new LinkedHashMap<>();
            Set<String> implausible = new LinkedHashSet<>();
            for (StoreOption option : item.storeOptions()) {
                if (option.packsTotalPrice() == null) {
                    continue;
                }
                if (option.packsPlausible()) {
                    cheapest.merge(option.storeName(), option.packsTotalPrice(), Math::min);
                } else {
                    implausible.add(option.storeName());
                }
            }
            cheapest.forEach((store, price) -> {
                double[] sum = perStore.computeIfAbsent(store, k -> new double[3]);
                sum[0]++;
                sum[1] += price;
            });
            for (String store : implausible) {
                if (!cheapest.containsKey(store)) {
                    perStore.computeIfAbsent(store, k -> new double[3])[2]++;
                }
            }
        }
        List<StoreTotal> out = new ArrayList<>();
        perStore.forEach((store, sum) -> out.add(new StoreTotal(store, (int) sum[0], (int) sum[2], toBuy.size(),
                Math.round(sum[1] * 100) / 100.0)));
        out.sort(Comparator.comparingInt(StoreTotal::linesCovered).reversed().thenComparingDouble(StoreTotal::total));
        return out;
    }
}
