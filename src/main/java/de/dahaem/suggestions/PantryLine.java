package de.dahaem.suggestions;

import java.time.LocalDate;
import java.util.List;

/**
 * One ingredient of a suggested recipe and where it stands: in stock, expiring soon, missing, or "to taste".
 * Missing ingredients carry the offers that are on sale today (every store, never one picked for the user).
 */
public record PantryLine(int itemId, String nameDeutsch, String nameEnglish, Status status, Double neededQuantity,
                         String neededUnit, int stockQuantity, String stockUnit, LocalDate expiresOn,
                         List<PantryOffer> offers) {

    /**
     * EXPIRED: some is in stock but its date has passed. It is shown (with the stock amount and the date, the user
     * decides), but it does not count as usable stock for the ranking: the ingredient counts as still to buy, and
     * carries the offers on sale like a missing one.
     */
    public enum Status { IN_STOCK, EXPIRING, EXPIRED, MISSING, TO_TASTE }

    public PantryLine withOffers(List<PantryOffer> newOffers) {
        return new PantryLine(itemId, nameDeutsch, nameEnglish, status, neededQuantity, neededUnit, stockQuantity,
                stockUnit, expiresOn, newOffers);
    }
}
