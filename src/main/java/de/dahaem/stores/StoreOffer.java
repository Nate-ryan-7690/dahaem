package de.dahaem.stores;

import java.time.LocalDate;

/**
 * One weekly offer as pulled from a chain's website; maps to a row in store_offer.
 *
 * salePrice is the price per unit during the offer. For multi-buy deals
 * ("3 for 5 €") dealQuantity is how many units you must buy and dealPrice the
 * total for them; a normal offer has dealQuantity 1 and dealPrice null.
 * normalPrice is the crossed-out "statt" price when the store shows one.
 * deposit is the Pfand per unit (null = none).
 * region is the store's price region (Penny), or "" for chains with one national
 * price list (ALDI Nord). It is part of the offer's identity, so it must not be null.
 */
public record StoreOffer(
        String externalId,
        String brand,
        String name,
        String salesUnit,
        double salePrice,
        int dealQuantity,
        Double dealPrice,
        Double normalPrice,
        Double basePrice,
        String baseUnit,
        Double deposit,
        String promoText,
        String category,
        String region,
        LocalDate validFrom,
        LocalDate validUntil) {

    /**
     * What you actually pay at the till to get this deal: all units plus their deposit.
     * This is the price the app shows and compares (Pfand refunds belong to finance, not shopping).
     */
    public double tillPrice() {
        double goods = dealPrice != null ? dealPrice : salePrice * dealQuantity;
        return goods + (deposit == null ? 0 : deposit * dealQuantity);
    }
}
