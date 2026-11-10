package de.dahaem.suggestions;

/**
 * How well one recipe is covered by what the household already has and by what is on sale. Plain data.
 * Ingredients that are only "to taste" (salt) are not counted. An ingredient is in stock when its inventory
 * quantity is above zero; the amount the recipe needs is not compared with the amount in stock yet.
 *
 * @param needed               ingredients the recipe needs (without "to taste")
 * @param inStock              of those, how many are in stock
 * @param expiring             of the in-stock ones, how many expire within the look-ahead window
 * @param missingOnSale        of the missing ones, how many are on sale in some store today
 * @param missingOnSaleAtStore of the missing ones, how many are on sale at the store that was asked for (0 when none)
 */
public record PantryCoverage(int recipeId, int needed, int inStock, int expiring, int missingOnSale,
                             int missingOnSaleAtStore) {

    /** Ingredients still to buy. */
    public int missing() {
        return needed - inStock;
    }

    /** Ingredients still to buy that are not on sale. */
    public int missingFullPrice() {
        return missing() - missingOnSale;
    }

    /** What makes the recipe fit now: ingredients in stock plus missing ones that are on sale. */
    public int matches() {
        return inStock + missingOnSale;
    }
}
