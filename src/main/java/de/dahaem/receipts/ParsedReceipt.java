package de.dahaem.receipts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured result of parsing one receipt's text. Fields the receipt didn't print are null.
 * {@code unparsedLines} holds every commercial line the handler didn't understand (never silently
 * dropped); payment/technical lines are removed before parsing and never appear here.
 */
public record ParsedReceipt(String storeBrand, String storeAddress, String storeId,
                            LocalDateTime purchasedAt, String currency,
                            List<ReceiptLine> lines, List<ReceiptAdjustment> adjustments,
                            BigDecimal total, List<TaxBreakdownEntry> taxBreakdown,
                            BigDecimal totalSavings, Integer loyaltyPointsEarned,
                            List<String> unparsedLines, String receiptNumber,
                            BigDecimal appSavings, BigDecimal otherSavings) {

    public ParsedReceipt {
        lines = List.copyOf(lines);
        adjustments = List.copyOf(adjustments);
        taxBreakdown = List.copyOf(taxBreakdown);
        unparsedLines = List.copyOf(unparsedLines);
    }

    /** What was actually paid for line {@code i}: printed total plus the discounts attached to it. */
    public BigDecimal paidTotal(int i) {
        BigDecimal paid = lines.get(i).lineTotal();
        for (ReceiptAdjustment a : adjustments) {
            if (a.type().isDiscount() && a.lineIndex() != null && a.lineIndex() == i) {
                paid = paid.add(a.amount());
            }
        }
        return paid;
    }

    /** Paid price per unit (or per kg for weighed goods) after discounts. */
    public BigDecimal paidUnitPrice(int i) {
        ReceiptLine line = lines.get(i);
        if (line.quantity() == null || line.quantity().signum() == 0) {
            return paidTotal(i);
        }
        return paidTotal(i).divide(line.quantity(), 2, java.math.RoundingMode.HALF_UP);
    }

    public List<ReceiptAdjustment> adjustmentsFor(int i) {
        return adjustments.stream().filter(a -> a.lineIndex() != null && a.lineIndex() == i).toList();
    }

    /** Sum of printed line totals plus every signed adjustment (discounts, deposits, returns). */
    public BigDecimal computedTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (ReceiptLine l : lines) {
            sum = sum.add(l.lineTotal());
        }
        for (ReceiptAdjustment a : adjustments) {
            sum = sum.add(a.amount());
        }
        return sum;
    }

    /** computedTotal minus the printed total; null if no total was found. */
    public BigDecimal delta() {
        return total == null ? null : computedTotal().subtract(total);
    }

    /** True if the parsed lines reconcile exactly with the printed total. Mismatch is reported, never thrown. */
    public boolean balances() {
        BigDecimal d = delta();
        return d != null && d.signum() == 0;
    }
}
