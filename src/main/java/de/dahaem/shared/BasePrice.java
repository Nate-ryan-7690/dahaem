package de.dahaem.shared;

/**
 * The price of a pack as a price per kilogram, per litre or per piece, so the comparison (and wrong pack data) shows up
 * in one place instead of in every client. Only for units that are recognised; a mass is shown per kg, a volume per l.
 */
public final class BasePrice {

    private BasePrice() {
    }

    /** {@code value} euros per {@code unit} ("kg", "l" or "Stück"). */
    public record Base(double value, String unit) {
    }

    /** The base price of a pack of {@code packSize} {@code packUnit} at {@code price}, or null when it cannot be told. */
    public static Base of(double price, int packSize, String packUnit) {
        Units.Parsed unit = Units.parse(packUnit);
        if (!unit.isKnown() || packSize <= 0) {
            return null;
        }
        double inBase = packSize * unit.toBaseFactor();
        return switch (unit.dimension()) {
            case MASS -> new Base(round(price / inBase * 1000), "kg");
            case VOLUME -> new Base(round(price / inBase * 1000), "l");
            case COUNT -> new Base(round(price / inBase), "Stück");
            default -> null;
        };
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
