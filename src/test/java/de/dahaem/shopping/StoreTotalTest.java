package de.dahaem.shopping;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The per-store totals of a list: cheapest option per line and store, bought lines left out, nothing picked. */
class StoreTotalTest {

    @Test
    void sumsTheCheapestOptionPerLineAndStoreAndSortsByCoverageThenPrice() {
        PersistedShoppingListItem milk = line(false, option("Aldi", 1.20, 1), option("Aldi", 0.99, 1), option("Penny", 1.10, 1));
        PersistedShoppingListItem rice = line(false, option("Penny", 2.00, 1));
        PersistedShoppingListItem flour = line(false);   // no store sells it

        List<StoreTotal> totals = StoreTotal.of(List.of(milk, rice, flour));

        assertEquals(2, totals.size());
        assertEquals(new StoreTotal("Penny", 2, 0, 3, 3.10), totals.get(0), "Penny covers two lines, so it comes first");
        assertEquals(new StoreTotal("Aldi", 1, 0, 3, 0.99), totals.get(1));
    }

    @Test
    void leavesTickedLinesAndOptionsWithoutAPackPriceOut() {
        PersistedShoppingListItem bought = line(true, option("Aldi", 5.00, 1));
        PersistedShoppingListItem unpriced = line(false, option("Aldi", null, 1));
        PersistedShoppingListItem open = line(false, option("Aldi", 1.00, 1));

        List<StoreTotal> totals = StoreTotal.of(List.of(bought, unpriced, open));

        assertEquals(List.of(new StoreTotal("Aldi", 1, 0, 2, 1.00)), totals);
        assertTrue(StoreTotal.of(List.of()).isEmpty());
    }

    @Test
    void anImplausiblePackCountIsNotInTheTotalButIsCounted() {
        // 300 packs of rice at 2.49 (a pack size of 1 g in the data) must not make Aldi cost 747 euros
        PersistedShoppingListItem rice = line(false, option("Aldi", 747.00, 300), option("Penny", 2.49, 1));
        PersistedShoppingListItem milk = line(false, option("Aldi", 0.99, 1));

        List<StoreTotal> totals = StoreTotal.of(List.of(rice, milk));

        assertEquals(new StoreTotal("Aldi", 1, 1, 2, 0.99), totals.stream().filter(t -> t.storeName().equals("Aldi")).findFirst().orElseThrow());
        assertEquals(new StoreTotal("Penny", 1, 0, 2, 2.49), totals.stream().filter(t -> t.storeName().equals("Penny")).findFirst().orElseThrow());
    }

    @Test
    void aSensibleOptionOfTheSameStoreWinsOverAnImplausibleOne() {
        PersistedShoppingListItem rice = line(false, option("Aldi", 747.00, 300), option("Aldi", 2.49, 1));

        assertEquals(List.of(new StoreTotal("Aldi", 1, 0, 1, 2.49)), StoreTotal.of(List.of(rice)));
    }

    @Test
    void thePackCountLimitIsTenAndUnknownCountsAreBelievable() {
        assertTrue(StoreOption.plausible(10));
        assertFalse(StoreOption.plausible(11));
        assertTrue(StoreOption.plausible(null));
    }

    private static PersistedShoppingListItem line(boolean purchased, StoreOption... options) {
        return new PersistedShoppingListItem(1, 1, "x", "x", 1, "Stück", options.length > 0, false, purchased, null, null,
                false, List.of(), List.of(options), null);
    }

    private static StoreOption option(String store, Double packsTotalPrice, int packsNeeded) {
        return new StoreOption(1, store, "Brand", "Stück", 1, 1.0, false, null, 1, null, null, null, null, 1.0, true,
                packsNeeded, packsTotalPrice, null, null, StoreOption.plausible(packsNeeded));
    }
}
