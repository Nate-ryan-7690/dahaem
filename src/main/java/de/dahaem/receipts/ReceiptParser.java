package de.dahaem.receipts;

import java.util.List;

/**
 * Turns one receipt's raw text into a {@link ParsedReceipt}. No SQL, no I/O: OCR and persistence
 * sit on either side of this class. Payment/technical footer data is stripped before parsing, and
 * odd or mis-OCR'd input never throws -- unrecognised lines land in {@code unparsedLines} and
 * {@link ParsedReceipt#balances()} reports whether the numbers reconcile.
 */
public class ReceiptParser {

    private final List<ReceiptStoreFormat> formats;

    public ReceiptParser() {
        this(List.of(new AldiReceiptFormat(), new PennyReceiptFormat()));
    }

    public ReceiptParser(List<ReceiptStoreFormat> formats) {
        this.formats = List.copyOf(formats);
    }

    /** Recognises the store from header markers; {@link StoreFormat#UNKNOWN} if none match. */
    public StoreFormat detectFormat(String rawText) {
        for (ReceiptStoreFormat f : formats) {
            if (f.matches(rawText)) {
                return f.format();
            }
        }
        return StoreFormat.UNKNOWN;
    }

    public ParsedReceipt parse(String rawText, StoreFormat format) {
        List<String> lines = ReceiptText.lines(rawText);
        for (ReceiptStoreFormat f : formats) {
            if (f.format() == format) {
                return f.parse(lines);
            }
        }
        // No handler: keep every (sanitised) line for review instead of failing.
        ReceiptBuilder b = new ReceiptBuilder();
        lines.forEach(b::unparsed);
        return b.build();
    }

    public ParsedReceipt parse(ReceiptTextSource source) {
        String text = source.text();
        return parse(text, detectFormat(text));
    }
}
