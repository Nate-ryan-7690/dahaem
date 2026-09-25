package de.dahaem.api;

import java.time.LocalDate;

/**
 * Body of POST /api/purchases: a purchase of a known store_item. {@code discountId} is optional,
 * {@code wasDiscounted} defaults to false, {@code purchaseDate} defaults to today.
 */
public record CreatePurchaseRequest(Integer storeItemId, Integer discountId, Boolean wasDiscounted,
                                    LocalDate purchaseDate, Integer quantityPurchased, Double pricePaid,
                                    LocalDate expirationDate) {
}
