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
 *
 * <p>basePrice is the price of one pack's worth per kg, per l or per piece (basePriceUnit "kg", "l", "Stück"; deposit not
 * included), so the comparison, and a wrong pack size, shows up here and not in every client; null when the unit is not
 * recognised. packsPlausible is false when packsNeeded is above {@link #MAX_PLAUSIBLE_PACKS}: check the pack size.
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
        LocalDate validFrom,
        LocalDate validUntil,
        Double deposit,
        Double tillPrice,
        boolean unitCompatible,
        Integer packsNeeded,
        Double packsTotalPrice,
        Double basePrice,
        String basePriceUnit,
        boolean packsPlausible
) {

    /**
     * More packs than this for one line is not believable (a "300 packs of rice" line is a wrong pack size in the data,
     * not a shopping trip). Such an option is flagged {@code packsPlausible = false} and left out of the store totals.
     * The threshold is a product decision (Nate); 10 matches the warning the page shows.
     */
    public static final int MAX_PLAUSIBLE_PACKS = 10;

    /** True when the pack count is believable, or unknown (units that do not reconcile have no count to doubt). */
    public static boolean plausible(Integer packsNeeded) {
        return packsNeeded == null || packsNeeded <= MAX_PLAUSIBLE_PACKS;
    }
}
