package de.dahaem.receipts;

import java.util.List;

/** Per-store handler: recognises its receipts by header markers and parses their lines. */
public interface ReceiptStoreFormat {

    StoreFormat format();

    /** True if the raw receipt text carries this store's header markers. */
    boolean matches(String rawText);

    /** Parses already-sanitised, non-blank lines (see {@code ReceiptText.lines}). Must not throw on odd input. */
    ParsedReceipt parse(List<String> lines);
}
