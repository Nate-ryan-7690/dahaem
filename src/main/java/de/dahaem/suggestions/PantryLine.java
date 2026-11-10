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

    public enum Status { IN_STOCK, EXPIRING, MISSING, TO_TASTE }

    public PantryLine withOffers(List<PantryOffer> newOffers) {
        return new PantryLine(itemId, nameDeutsch, nameEnglish, status, neededQuantity, neededUnit, stockQuantity,
                stockUnit, expiresOn, newOffers);
    }
}
