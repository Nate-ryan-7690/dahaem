package de.dahaem.suggestions;

import java.time.LocalDate;

/** One offer that is on sale today for an ingredient: the store, the pack it is for, and the prices. */
public record PantryOffer(String storeName, int packSize, String packUnit, double salePrice, Double regularPrice,
                          LocalDate validUntil) {
}
