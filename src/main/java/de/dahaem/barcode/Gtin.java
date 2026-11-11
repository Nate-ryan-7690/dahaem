package de.dahaem.barcode;

/**
 * Barcode numbers (GTIN: EAN-8, UPC-A, EAN-13, GTIN-14). {@link #normalize} accepts what a person types (spaces and
 * hyphens allowed), checks the length and the check digit, and returns the number in one form: a 12-digit UPC-A is the
 * same product as the EAN-13 with a leading 0, so it is stored as that; 8 and 14 digits stay as they are.
 */
public final class Gtin {

    private Gtin() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidBarcodeException("INVALID_BARCODE", "A barcode is required");
        }
        String digits = raw.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d+")) {
            throw new InvalidBarcodeException("INVALID_BARCODE", "A barcode has digits only");
        }
        int length = digits.length();
        if (length != 8 && length != 12 && length != 13 && length != 14) {
            throw new InvalidBarcodeException("INVALID_BARCODE",
                    "A barcode has 8, 12, 13 or 14 digits, this one has " + length);
        }
        if (!checkDigitFits(digits)) {
            throw new InvalidBarcodeException("BARCODE_CHECKSUM",
                    "The check digit does not fit: one of the digits was probably mistyped");
        }
        return length == 12 ? "0" + digits : digits;
    }

    /** The last digit must make the weighted sum (weights 3, 1, 3, 1 ... from the right, check digit excluded) a multiple of 10. */
    static boolean checkDigitFits(String digits) {
        int sum = 0;
        int weight = 3;
        for (int i = digits.length() - 2; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 3 ? 1 : 3;
        }
        int expected = (10 - sum % 10) % 10;
        return expected == digits.charAt(digits.length() - 1) - '0';
    }
}
