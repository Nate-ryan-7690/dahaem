package de.dahaem.barcode;

/**
 * What an open data source says about a barcode DAHÄM does not know yet. A suggestion only: nothing is saved until the
 * user assigns the barcode to a catalogue item. packSize / packUnit are null when the quantity text could not be read.
 */
public record ProductSuggestion(String name, String brand, Integer packSize, String packUnit, String quantityText,
                                String source) {
}
