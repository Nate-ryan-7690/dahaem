package de.dahaem.receipts;

import java.math.BigDecimal;

/** One row of the receipt's own tax table; net/tax/gross are null when the row didn't print all three. */
public record TaxBreakdownEntry(String symbol, BigDecimal ratePercent, BigDecimal net, BigDecimal tax, BigDecimal gross) {
}
