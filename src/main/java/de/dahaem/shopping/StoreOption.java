package de.dahaem.shopping;

import java.time.LocalDate;

/**
 * The chosen way to buy one shopping-list line item at one store: what it costs today,
 * whether that's a sale price, and the price you'd actually see at the till (sale/deal
 * price where one applies, plus deposit).
 *
 * <p>unitCompatible says whether this pack's unit is even the same physical dimension (mass,
 * volume or count) as what the line needs — see {@link de.dahaem.shared.Units}. Only when it is
 * true do packsNeeded/packsTotalPrice carry a real pack count and its total price; when the units
 * don't reconcile (or either is unrecognized), both stay null rather than fabricate a number from
 * a price that isn't actually for the needed quantity.
 */
public record StoreOption(
        int storeItemId,
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
        Double tillPrice,
        boolean unitCompatible,
        Integer packsNeeded,
        Double packsTotalPrice
) {
}
