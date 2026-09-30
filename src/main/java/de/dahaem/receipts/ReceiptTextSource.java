package de.dahaem.receipts;

/**
 * Seam for whatever turns a receipt into text (OCR of a photo, a pasted string, a test fixture).
 * The parser only ever sees the text, so the OCR engine can be chosen later without touching it.
 */
public interface ReceiptTextSource {
    String text();
}
