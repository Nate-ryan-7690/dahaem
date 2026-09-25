package de.dahaem.api;

import de.dahaem.finance.Purchase;

import java.time.LocalDate;

public record PurchaseDto(int id, int storeItemId, Integer discountId, boolean wasDiscounted, LocalDate purchaseDate,
                          int quantityPurchased, double pricePaid, LocalDate expirationDate) {

    static PurchaseDto from(Purchase purchase) {
        return new PurchaseDto(purchase.getId(), purchase.getStoreItemId(), purchase.getDiscountId(),
                purchase.isWasDiscounted(), purchase.getPurchaseDate(), purchase.getQuantityPurchased(),
                purchase.getPricePaid(), purchase.getExpirationDate());
    }
}
