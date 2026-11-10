package de.dahaem.api;

import de.dahaem.suggestions.PantryOffer;

import java.time.LocalDate;

/**
 * An offer that is on sale today for an ingredient: the store, the pack it is for ({@code packSize} in
 * {@code packUnit}, e.g. 600 g; a price without its pack misleads), the sale price and the regular price (null when
 * unknown), both for that pack in euros, and the last day of the offer. Offers are listed by store name and pack
 * size, not by price: the user chooses.
 */
public record PantryOfferDto(String storeName, int packSize, String packUnit, double salePrice, Double regularPrice,
                             LocalDate validUntil) {

    static PantryOfferDto from(PantryOffer offer) {
        return new PantryOfferDto(offer.storeName(), offer.packSize(), offer.packUnit(), offer.salePrice(),
                offer.regularPrice(), offer.validUntil());
    }
}
