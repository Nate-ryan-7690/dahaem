package de.dahaem.shopping;

import java.time.LocalDate;

/**
 * The chosen way to buy one shopping-list line item at one store: what it costs today,
 * whether that's a sale price, and the price you'd actually see at the till (sale/deal
 * price where one applies, plus deposit).
 */
public record StoreOption(
        String storeName,
        String brandName,
        String packageUnit,
        int packageSize,
        Double normalPrice,
        boolean onSale,
        Double salePrice,
        int dealQuantity,
        Double dealPrice,
        LocalDate validUntil,
        Double deposit,
        Double tillPrice
) {
}
