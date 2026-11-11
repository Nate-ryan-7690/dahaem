package de.dahaem.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How much of an item's unit one pack is. */
class StockAmountsTest {

    @Test
    void aPackAddsItsSizeInTheItemsUnit() {
        assertEquals(500, StockAmounts.perPack(500, "g", "g"));
        assertEquals(1000, StockAmounts.perPack(1, "kg", "g"));
        assertEquals(1500, StockAmounts.perPack(1500, "ml", "ml"));
        assertEquals(2000, StockAmounts.perPack(2, "l", "ml"));
        assertEquals(6, StockAmounts.perPack(6, "stück", "Stück"));
        assertEquals(1, StockAmounts.perPack(1000, "g", "kg"));
    }

    @Test
    void anUnrecognisedUnitCountsAsItIsWhenItIsTheItemsOwnUnit() {
        assertEquals(20, StockAmounts.perPack(20, "WL", "wl"));
    }

    @Test
    void whatCannotBeConvertedCountsAsOnePerPackLikeBeforePacksHadSizes() {
        assertEquals(1, StockAmounts.perPack(500, "ml", "g"));
        assertEquals(1, StockAmounts.perPack(6, "stück", "g"));
        assertEquals(1, StockAmounts.perPack(3, "Eimer", "g"));
    }

    @Test
    void aTinyPackIsAtLeastOne() {
        assertEquals(1, StockAmounts.perPack(1, "g", "kg"));
    }
}
