package de.dahaem.stores;

/**
 * One store_offer row loaded for discount promotion, with its store id.
 * salePrice is the flyer price (the discount). normalPrice is the "was" price when the
 * flyer shows one, else null (then the bootstrapped store_item has no known normal price).
 * storeName / source / externalId / the dates identify the offer in the review reports, so a row can be checked against the flyer.
 */
public record OfferRow(
        int storeId,
        String externalId,
        String brand,
        String name,
        String category,
        String salesUnit,
        double salePrice,
        int dealQuantity,
        Double dealPrice,
        Double normalPrice,
        Double deposit,
        String promoText,
        String validFrom,
        String validUntil,
        String source,
        String storeName) {
}
