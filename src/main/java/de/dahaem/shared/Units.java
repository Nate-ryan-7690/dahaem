package de.dahaem.shared;

import java.util.Locale;
import java.util.Set;

/**
 * Parses a recipe/store unit string into a physical dimension plus a factor to its base unit, so
 * quantities can be compared or converted only when it is actually meaningful. Two units are
 * comparable only within the same dimension (mass with mass, volume with volume, count with
 * count) — this deliberately never converts across dimensions (e.g. no density-based g&harr;ml
 * guessing), since that would fabricate a number nobody can trust.
 */
public final class Units {

    private Units() {
    }

    public enum Dimension { MASS, VOLUME, COUNT, UNKNOWN }

    /** A parsed unit: its dimension, and the factor that converts a quantity in it to the dimension's base unit. */
    public record Parsed(Dimension dimension, double toBaseFactor) {
        public static final Parsed UNKNOWN = new Parsed(Dimension.UNKNOWN, 1.0);

        public boolean isKnown() {
            return dimension != Dimension.UNKNOWN;
        }
    }

    // Count units: German package words treated as "one piece" each — no numeric conversion needed,
    // just a shared dimension so e.g. "10 Stück" and a store's "Packung" of eggs can be compared.
    private static final Set<String> COUNT_UNITS = Set.of(
            "stuck", "stucke", "stk", "st",
            "pcs", "pc",
            "packung", "packungen", "pack", "packs",
            "dose", "dosen",
            "bund", "bunde", "bunden",
            "glas", "glaser"
    );

    /** Parses a unit string, case-insensitively and tolerant of German umlauts and trailing junk. */
    public static Parsed parse(String rawUnit) {
        if (rawUnit == null) {
            return Parsed.UNKNOWN;
        }
        String u = normalize(rawUnit);
        if (u.isEmpty()) {
            return Parsed.UNKNOWN;
        }
        return switch (u) {
            case "g", "gr", "gramm", "gramme" -> new Parsed(Dimension.MASS, 1.0);
            case "kg", "kilogramm", "kilo" -> new Parsed(Dimension.MASS, 1000.0);
            case "ml", "milliliter" -> new Parsed(Dimension.VOLUME, 1.0);
            case "cl", "zentiliter" -> new Parsed(Dimension.VOLUME, 10.0);
            case "l", "liter" -> new Parsed(Dimension.VOLUME, 1000.0);
            default -> COUNT_UNITS.contains(u) ? new Parsed(Dimension.COUNT, 1.0) : Parsed.UNKNOWN;
        };
    }

    private static String normalize(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace("ü", "u")
                .replace("ä", "a")
                .replace("ö", "o");
        s = s.replaceAll("[.\\s]+$", "");
        return s;
    }

    /** True only when both units are known and share the same dimension; never true across dimensions. */
    public static boolean compatible(String unitA, String unitB) {
        Parsed a = parse(unitA);
        Parsed b = parse(unitB);
        return a.isKnown() && b.isKnown() && a.dimension() == b.dimension();
    }

    /** The quantity converted to its dimension's base unit, or null when the unit isn't recognized. */
    public static Double toBase(double quantity, String unit) {
        Parsed parsed = parse(unit);
        return parsed.isKnown() ? quantity * parsed.toBaseFactor() : null;
    }
}
