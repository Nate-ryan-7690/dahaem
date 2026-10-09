package de.dahaem.stores;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plausibility gate between "the name matched an inventory_item" and "create a store_item + discount".
 * A match that fails creates nothing; the offer goes to the review report instead.
 *
 * UNIT: the pack's unit family must fit the item's unit. Families are weight (g, kg), volume (ml, cl, l) and
 * pieces (Stück), plus "WL" (wash loads) as a family of its own. An item whose unit is a container word ("Packung", "Flasche", "Glas", "Dose", ...) carries
 * no quantity, so it accepts a pack of any family: there is nothing to contradict. A weight item does not
 * accept a volume or piece pack (a 750 ml soft drink is not "zitrone"; "15 Stück" is not a number of grams).
 *
 * PRICE: price per base unit (g / ml / piece) of the offer versus the median of the item's reference prices
 * in the same family, beyond {@link #MAX_RATIO} either way fails. References come only from STORE-sourced
 * data (store_product regular prices); aggregator offers are not used because their prices are what is
 * being checked. With fewer than {@link #MIN_REFERENCES} references there is no basis, so the check passes.
 */
final class MatchGate {

    static final double MAX_RATIO = 3.0;
    static final int MIN_REFERENCES = 3;

    record Verdict(boolean ok, String reason) {
        static final Verdict OK = new Verdict(true, "");
    }

    /** Reference prices per base unit, by inventory_item id and unit family. */
    static final class References {
        private final Map<Integer, Map<String, List<Double>>> byItem = new HashMap<>();

        void add(int itemId, StorePromotionService.Pack pack, double price) {
            String family = packFamily(pack.unit());
            if (family != null && pack.size() > 0 && price > 0) {
                byItem.computeIfAbsent(itemId, k -> new HashMap<>())
                        .computeIfAbsent(family, k -> new ArrayList<>()).add(price / pack.size());
            }
        }

        List<Double> of(int itemId, String family) {
            return byItem.getOrDefault(itemId, Map.of()).getOrDefault(family, List.of());
        }
    }

    private MatchGate() {
    }

    static Verdict check(String itemUnit, StorePromotionService.Pack pack, double salePrice,
                         int itemId, References references) {
        return check(itemUnit, pack, salePrice, itemId, references, false);
    }

    /**
     * @param skipPrice true when the match comes from an override: that is a human decision, so the plausibility of the
     *                  price is not second-guessed. The UNIT check still runs (the pack must be readable and fit).
     */
    static Verdict check(String itemUnit, StorePromotionService.Pack pack, double salePrice,
                         int itemId, References references, boolean skipPrice) {
        String packFamily = packFamily(pack.unit());
        String itemFamily = itemFamily(itemUnit);
        if (itemFamily != null && packFamily != null && !itemFamily.equals(packFamily)) {
            return new Verdict(false, "unit: item unit '" + itemUnit + "' vs pack " + pack.size() + " " + pack.unit());
        }
        if (skipPrice || packFamily == null || pack.size() <= 0 || salePrice <= 0) {
            return Verdict.OK;
        }
        List<Double> refs = references.of(itemId, packFamily);
        if (refs.size() < MIN_REFERENCES) {
            return Verdict.OK;
        }
        double median = median(refs);
        double perUnit = salePrice / pack.size();
        double ratio = perUnit / median;
        if (ratio > MAX_RATIO || ratio < 1 / MAX_RATIO) {
            return new Verdict(false, String.format(Locale.ROOT,
                    "price: %.5f per %s vs reference median %.5f (x%.1f, n=%d)",
                    perUnit, pack.unit(), median, ratio, refs.size()));
        }
        return Verdict.OK;
    }

    /** "g"/"kg" -> g, "ml"/"cl"/"l" -> ml, "stück"/"st" -> stück; null for anything else (container words). */
    static String itemFamily(String unit) {
        return unit == null ? null : packFamily(unit.trim().toLowerCase(Locale.GERMAN));
    }

    static String packFamily(String unit) {
        if (unit == null) {
            return null;
        }
        return switch (unit.toLowerCase(Locale.GERMAN)) {
            case "g", "kg", "mg" -> "g";
            case "ml", "cl", "l" -> "ml";
            case "stück", "stueck", "st", "stk" -> "stück";
            case "wl" -> "WL";                                    // Waschladungen: a count unit of its own
            default -> null;
        };
    }

    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }
}
