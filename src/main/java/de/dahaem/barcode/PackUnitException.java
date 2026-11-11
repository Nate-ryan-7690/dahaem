package de.dahaem.barcode;

/**
 * The pack of a barcode cannot be stocked in the catalogue item's unit (a pack in ml for an item counted in g, or a unit
 * nobody knows). A bad request (400) with its own code, PACK_UNIT_MISMATCH, so a client can show a precise message.
 */
public class PackUnitException extends IllegalArgumentException {

    public PackUnitException(String message) {
        super(message);
    }
}
