package de.dahaem.barcode;

/**
 * A barcode number that cannot be right: not digits, a length no barcode has (INVALID_BARCODE), or a check digit that
 * does not fit the other digits, which is what a typing error looks like (BARCODE_CHECKSUM). A bad request (400) that
 * carries its own code so a client can show a precise message.
 */
public class InvalidBarcodeException extends IllegalArgumentException {

    private final String code;

    public InvalidBarcodeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
