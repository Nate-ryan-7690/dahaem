package de.dahaem.suggestions;

import java.util.Locale;

/** The orders offered for the pantry-first suggestions; the user picks one, the default is {@link #BEST}. */
public enum PantrySort {
    /** Weighted: uses up expiring food first, then food in stock, and fewest ingredients to buy at full price. */
    BEST("best"),
    /** Fewest ingredients to buy, then fewest of them at full price. */
    FEWEST_TO_BUY("fewest-to-buy"),
    /** Uses the most ingredients that expire soon. */
    EXPIRING("expiring"),
    /** By recipe name. */
    NAME("name"),
    /** Shortest total time first (recipes without a time last). */
    TIME("time");

    private final String value;

    PantrySort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Null or blank is {@link #BEST}; an unknown value is an IllegalArgumentException (the API answers 400). */
    public static PantrySort parse(String text) {
        if (text == null || text.isBlank()) {
            return BEST;
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (PantrySort sort : values()) {
            if (sort.value.equals(wanted)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("'sort' must be one of best, fewest-to-buy, expiring, name, time");
    }
}
