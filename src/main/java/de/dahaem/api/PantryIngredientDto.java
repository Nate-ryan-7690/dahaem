package de.dahaem.api;

import de.dahaem.suggestions.PantryLine;

import java.time.LocalDate;
import java.util.List;

/**
 * One ingredient of a suggested recipe. {@code status} is {@code IN_STOCK}, {@code EXPIRING} (in stock and expires
 * within the look-ahead), {@code EXPIRED} (some is in stock but its date has passed: shown with the amount and the
 * date, not counted as usable stock), {@code MISSING} or {@code TO_TASTE}. {@code neededQuantity} / {@code neededUnit} is what
 * the recipe asks for (quantity null for "to taste"); {@code stockQuantity} / {@code stockUnit} is what the household
 * has, in the item's own unit; {@code expiresOn} is the earliest expiry date of the stock (null when unknown).
 * {@code offers} lists, for a missing or expired ingredient, every offer that is on sale today (see {@link PantryOfferDto});
 * empty otherwise.
 */
public record PantryIngredientDto(int itemId, String nameDeutsch, String nameEnglish, String status,
                                  Double neededQuantity, String neededUnit, int stockQuantity, String stockUnit,
                                  LocalDate expiresOn, List<PantryOfferDto> offers) {

    static PantryIngredientDto from(PantryLine line) {
        return new PantryIngredientDto(line.itemId(), line.nameDeutsch(), line.nameEnglish(), line.status().name(),
                line.neededQuantity(), line.neededUnit(), line.stockQuantity(), line.stockUnit(), line.expiresOn(),
                line.offers().stream().map(PantryOfferDto::from).toList());
    }
}
