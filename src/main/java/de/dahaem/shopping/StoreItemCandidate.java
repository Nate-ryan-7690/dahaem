package de.dahaem.shopping;

import java.time.LocalDate;

/**
 * One store_item that can satisfy an inventory item, with its brand/store and (if one is
 * running today) its current store_discount. productLastSeen/productAvailable come from the
 * linked store_product and are null when the store_item has no such link (e.g. a flyer-only item).
 */
public record StoreItemCandidate(
        int storeItemId,
        String storeName,
        String brandName,
        String packageUnit,
        int packageSize,
        Double normalPrice,
        Double deposit,
        boolean storeItemAvailable,
        LocalDate productLastSeen,
        Boolean productAvailable,
        Double discountPrice,
        LocalDate discountValidUntil,
        int dealQuantity,
        Double dealPrice
) {
}
