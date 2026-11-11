package de.dahaem.finance;

import de.dahaem.shared.Units;

/**
 * Stock is counted in the catalogue item's own unit (g, ml, Stück), as the shopping list compares it with a line's unit.
 * A purchase is counted in packs, so one pack adds {@link #perPack}: the pack's size converted to the item's unit.
 */
public final class StockAmounts {

    private StockAmounts() {
    }

    /**
     * How much of the item's unit one pack is: 500 g into an item counted in g is 500, 1000 g into an item counted in kg is
     * 1. The units must be of the same dimension (mass, volume, count); the same unit of an unrecognised kind (WL) counts as
     * it is. Anything that cannot be converted (a pack in pieces for an item in g) counts as 1 per pack, which is what a
     * purchase did before packs had sizes. At least 1.
     */
    public static int perPack(int packSize, String packUnit, String itemUnit) {
        Units.Parsed pack = Units.parse(packUnit);
        Units.Parsed item = Units.parse(itemUnit);
        if (pack.isKnown() && item.isKnown() && pack.dimension() == item.dimension()) {
            long amount = Math.round(packSize * pack.toBaseFactor() / item.toBaseFactor());
            return (int) Math.max(1, Math.min(amount, 1_000_000_000L));
        }
        if (packUnit != null && itemUnit != null && packUnit.trim().equalsIgnoreCase(itemUnit.trim())) {
            return Math.max(1, packSize);
        }
        return 1;
    }
}
