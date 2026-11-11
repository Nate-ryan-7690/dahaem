package de.dahaem.finance;

import java.time.LocalDate;

/**
 * One batch of an item's stock = one purchase: where and when it was bought, when it expires, how many packs of which size
 * were bought, and how much of it is left in the item's own unit ({@code unit}). Used earliest-expiring first.
 */
public record StockBatch(int purchaseId, int storeItemId, String storeName, String brandName, LocalDate purchaseDate,
                         LocalDate expirationDate, int packs, int packSize, String packUnit, int remaining, String unit) {
}
