package de.dahaem.suggestions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The ranking rules are pure functions, so these tests need no database. */
class PantryRankingTest {

    private static PantryCoverage c(int id, int needed, int inStock, int expiring, int missingOnSale) {
        return new PantryCoverage(id, needed, inStock, expiring, missingOnSale, 0);
    }

    private static List<Integer> ids(List<PantryCoverage> list) {
        return list.stream().map(PantryCoverage::recipeId).toList();
    }

    @Test
    void usingUpExpiringFoodRanksAboveMerelyHavingIt() {
        PantryCoverage expiring = c(1, 4, 2, 2, 0);
        PantryCoverage plain = c(2, 4, 2, 0, 0);
        assertEquals(List.of(1, 2), ids(PantryRanking.order(List.of(plain, expiring), PantrySort.BEST)));
    }

    @Test
    void pantryIngredientsRankARecipeHigherNotLower() {
        // same recipe size: more of it in the pantry must score higher
        assertEquals(true, PantryRanking.score(c(1, 5, 4, 0, 0)) > PantryRanking.score(c(2, 5, 1, 0, 0)));
    }

    @Test
    void aGapOnSaleIsBetterThanAGapAtFullPrice() {
        PantryCoverage onSale = c(1, 4, 2, 0, 2);
        PantryCoverage fullPrice = c(2, 4, 2, 0, 0);
        assertEquals(List.of(1, 2), ids(PantryRanking.order(List.of(fullPrice, onSale), PantrySort.BEST)));
    }

    @Test
    void aFullyStockedRecipeBeatsOneThatNeedsShopping() {
        PantryCoverage allThere = c(1, 3, 3, 0, 0);
        PantryCoverage allOnSale = c(2, 3, 0, 0, 3);
        assertEquals(List.of(1, 2), ids(PantryRanking.order(List.of(allOnSale, allThere), PantrySort.BEST)));
    }

    @Test
    void tiesEndByRecipeIdSoPagingIsStable() {
        List<PantryCoverage> same = List.of(c(9, 3, 1, 0, 0), c(4, 3, 1, 0, 0), c(7, 3, 1, 0, 0));
        assertEquals(List.of(4, 7, 9), ids(PantryRanking.order(same, PantrySort.BEST)));
        assertEquals(List.of(4, 7, 9), ids(PantryRanking.order(same, PantrySort.FEWEST_TO_BUY)));
        assertEquals(List.of(4, 7, 9), ids(PantryRanking.order(same, PantrySort.EXPIRING)));
    }

    @Test
    void fewestToBuyOrdersByGapsThenByGapsAtFullPrice() {
        PantryCoverage oneGap = c(1, 5, 4, 0, 0);
        PantryCoverage twoGapsOnSale = c(2, 5, 3, 0, 2);
        PantryCoverage twoGapsFull = c(3, 5, 3, 0, 0);
        assertEquals(List.of(1, 2, 3),
                ids(PantryRanking.order(List.of(twoGapsFull, twoGapsOnSale, oneGap), PantrySort.FEWEST_TO_BUY)));
    }

    @Test
    void expiringSortPutsTheMostExpiringIngredientsFirst() {
        PantryCoverage two = c(1, 6, 3, 2, 0);
        PantryCoverage one = c(2, 3, 3, 1, 0);
        PantryCoverage none = c(3, 3, 3, 0, 0);
        assertEquals(List.of(1, 2, 3), ids(PantryRanking.order(List.of(none, one, two), PantrySort.EXPIRING)));
    }

    @Test
    void filterKeepsRecipesThatFitAndTheAskedStore() {
        PantryCoverage nothing = c(1, 3, 0, 0, 0);
        PantryCoverage fits = c(2, 3, 1, 0, 0);
        PantryCoverage atStore = new PantryCoverage(3, 3, 0, 0, 1, 1);
        assertEquals(List.of(2, 3), ids(PantryRanking.filter(List.of(nothing, fits, atStore), 1, false)));
        assertEquals(List.of(3), ids(PantryRanking.filter(List.of(nothing, fits, atStore), 1, true)));
        assertEquals(List.of(), ids(PantryRanking.filter(List.of(nothing, fits, atStore), 2, false)));
    }

    @Test
    void coverageCountsAddUp() {
        PantryCoverage cov = c(1, 6, 4, 1, 1);
        assertEquals(2, cov.missing());
        assertEquals(1, cov.missingFullPrice());
        assertEquals(5, cov.matches());
    }

    @Test
    void sortParsingDefaultsToBestAndRejectsUnknownValues() {
        assertEquals(PantrySort.BEST, PantrySort.parse(null));
        assertEquals(PantrySort.BEST, PantrySort.parse(" "));
        assertEquals(PantrySort.FEWEST_TO_BUY, PantrySort.parse("Fewest-To-Buy"));
        assertEquals(PantrySort.TIME, PantrySort.parse("time"));
        assertThrows(IllegalArgumentException.class, () -> PantrySort.parse("cheapest"));
    }
}
