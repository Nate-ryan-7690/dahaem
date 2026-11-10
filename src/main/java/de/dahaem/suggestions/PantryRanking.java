package de.dahaem.suggestions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ranking rules of the pantry-first suggestions, as pure functions (no database). The purpose is healthy
 * meals at the lowest shopping cost: use up what is already in the pantry before it expires, and buy as little
 * as possible, preferably what is on sale. So pantry ingredients rank a recipe HIGHER, never lower.
 * The weights below are the product decision to tune; every order is a choice the user can change.
 */
public final class PantryRanking {

    /** An in-stock ingredient that expires soon (use it up first). */
    static final int WEIGHT_EXPIRING = 4;
    /** An in-stock ingredient that does not expire soon (already paid for). */
    static final int WEIGHT_IN_STOCK = 2;
    /** A missing ingredient that is on sale (cheaper to buy). */
    static final int WEIGHT_MISSING_ON_SALE = -1;
    /** A missing ingredient at full price (the most expensive kind of gap). */
    static final int WEIGHT_MISSING_FULL_PRICE = -3;

    private PantryRanking() {
    }

    /** The weighted score used by {@link PantrySort#BEST}: higher is better. */
    public static int score(PantryCoverage c) {
        int inStockNotExpiring = c.inStock() - c.expiring();
        return WEIGHT_EXPIRING * c.expiring()
                + WEIGHT_IN_STOCK * inStockNotExpiring
                + WEIGHT_MISSING_ON_SALE * c.missingOnSale()
                + WEIGHT_MISSING_FULL_PRICE * c.missingFullPrice();
    }

    /**
     * Orders coverages for BEST, FEWEST_TO_BUY or EXPIRING. Ties always end by recipe id, so the order is stable
     * and paging neither repeats nor skips a recipe. NAME and TIME are ordered by the recipe search, not here.
     */
    public static List<PantryCoverage> order(List<PantryCoverage> coverages, PantrySort sort) {
        Comparator<PantryCoverage> byId = Comparator.comparingInt(PantryCoverage::recipeId);
        Comparator<PantryCoverage> comparator = switch (sort) {
            case BEST -> Comparator.comparingInt(PantryRanking::score).reversed()
                    .thenComparingInt(PantryCoverage::missing)
                    .thenComparing(Comparator.comparingInt(PantryCoverage::inStock).reversed())
                    .thenComparing(byId);
            case FEWEST_TO_BUY -> Comparator.comparingInt(PantryCoverage::missing)
                    .thenComparingInt(PantryCoverage::missingFullPrice)
                    .thenComparing(Comparator.comparingInt(PantryCoverage::inStock).reversed())
                    .thenComparing(byId);
            case EXPIRING -> Comparator.comparingInt(PantryCoverage::expiring).reversed()
                    .thenComparingInt(PantryCoverage::missing)
                    .thenComparing(byId);
            default -> throw new IllegalArgumentException("not ordered here: " + sort);
        };
        List<PantryCoverage> sorted = new ArrayList<>(coverages);
        sorted.sort(comparator);
        return sorted;
    }

    /**
     * Keeps the coverages that fit now: at least {@code minMatches} ingredients in stock or on sale, and, when
     * {@code storeAsked}, at least one missing ingredient on sale at that store.
     */
    public static List<PantryCoverage> filter(List<PantryCoverage> coverages, int minMatches, boolean storeAsked) {
        List<PantryCoverage> kept = new ArrayList<>();
        for (PantryCoverage c : coverages) {
            if (c.needed() > 0 && c.matches() >= minMatches && (!storeAsked || c.missingOnSaleAtStore() > 0)) {
                kept.add(c);
            }
        }
        return kept;
    }
}
