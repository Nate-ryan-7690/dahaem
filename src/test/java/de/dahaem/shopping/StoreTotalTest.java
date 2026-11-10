package de.dahaem.shopping;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The per-store totals of a list: cheapest option per line and store, bought lines left out, nothing picked. */
class StoreTotalTest {

    @Test
    void sumsTheCheapestOptionPerLineAndStoreAndSortsByCoverageThenPrice() {
        PersistedShoppingListItem milk = line(false, option("Aldi", 1.20), option("Aldi", 0.99), option("Penny", 1.10));
        PersistedShoppingListItem rice = line(false, option("Penny", 2.00));
        PersistedShoppingListItem flour = line(false);   // no store sells it

        List<StoreTotal> totals = StoreTotal.of(List.of(milk, rice, flour));

        assertEquals(2, totals.size());
        assertEquals(new StoreTotal("Penny", 2, 3, 3.10), totals.get(0), "Penny covers two lines, so it comes first");
        assertEquals(new StoreTotal("Aldi", 1, 3, 0.99), totals.get(1));
    }

    @Test
    void leavesTickedLinesAndOptionsWithoutAPackPriceOut() {
        PersistedShoppingListItem bought = line(true, option("Aldi", 5.00));
        PersistedShoppingListItem unpriced = line(false, option("Aldi", null));
        PersistedShoppingListItem open = line(false, option("Aldi", 1.00));

        List<StoreTotal> totals = StoreTotal.of(List.of(bought, unpriced, open));

        assertEquals(List.of(new StoreTotal("Aldi", 1, 2, 1.00)), totals);
        assertTrue(StoreTotal.of(List.of()).isEmpty());
    }

    private static PersistedShoppingListItem line(boolean purchased, StoreOption... options) {
        return new PersistedShoppingListItem(1, 1, "x", "x", 1, "Stück", options.length > 0, false, purchased, null, null,
                false, List.of(), List.of(options), null);
    }

    private static StoreOption option(String store, Double packsTotalPrice) {
        return new StoreOption(1, store, "Brand", "Stück", 1, 1.0, false, null, 1, null, null, null, null, 1.0, true, 1,
                packsTotalPrice);
    }
}
