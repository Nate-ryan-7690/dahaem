package de.dahaem.barcode;

/**
 * The answer to "what is this barcode?". {@code code} is the normalised number. {@code status}:
 * KNOWN (it is in DAHÄM, see {@code product}); FOUND (not in DAHÄM, an open data source describes it, see
 * {@code suggestion}); NOT_FOUND (neither); DISABLED (not in DAHÄM and the outside lookup is switched off);
 * UNAVAILABLE (the outside lookup was tried and failed: try again or enter the product by hand).
 */
public record BarcodeLookup(String code, String status, BarcodeProduct product, ProductSuggestion suggestion) {
}
