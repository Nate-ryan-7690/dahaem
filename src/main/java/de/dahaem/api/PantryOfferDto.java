package de.dahaem.api;

import de.dahaem.shared.BasePrice;
import de.dahaem.suggestions.PantryOffer;

import java.time.LocalDate;

/**
 * An offer that is on sale today for an ingredient: the store, the pack it is for ({@code packSize} in
 * {@code packUnit}, e.g. 600 g; a price without its pack misleads), the sale price and the regular price (null when
 * unknown), both for that pack in euros, and the last day of the offer. Offers are listed by store name and pack
 * size, not by price: the user chooses. {@code salePricePerBase} / {@code regularPricePerBase} are the same prices per kg,
 * per l or per piece ({@code basePriceUnit} "kg", "l", "Stück"), null when the unit is not recognised.
 */
public record PantryOfferDto(String storeName, int packSize, String packUnit, double salePrice, Double regularPrice,
                             LocalDate validUntil, Double salePricePerBase, Double regularPricePerBase,
                             String basePriceUnit) {

    static PantryOfferDto from(PantryOffer offer) {
        BasePrice.Base sale = BasePrice.of(offer.salePrice(), offer.packSize(), offer.packUnit());
        BasePrice.Base regular = offer.regularPrice() == null ? null
                : BasePrice.of(offer.regularPrice(), offer.packSize(), offer.packUnit());
        return new PantryOfferDto(offer.storeName(), offer.packSize(), offer.packUnit(), offer.salePrice(),
                offer.regularPrice(), offer.validUntil(), sale == null ? null : sale.value(),
                regular == null ? null : regular.value(), sale == null ? null : sale.unit());
    }
}
