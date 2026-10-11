package de.dahaem.receipts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable accumulator shared by the store handlers. Tracks whether the previous line was an item
 * (or one of its sub-lines) so discounts and deposits attach to a line only when that's determinable.
 */
final class ReceiptBuilder {

    private final List<ReceiptLine> lines = new ArrayList<>();
    private final List<ReceiptAdjustment> adjustments = new ArrayList<>();
    private final List<TaxBreakdownEntry> taxes = new ArrayList<>();
    private final List<String> unparsed = new ArrayList<>();
    private boolean adjacent;

    String brand;
    String address;
    String storeId;
    LocalDateTime purchasedAt;
    BigDecimal total;
    BigDecimal savings;
    BigDecimal appSavings;
    BigDecimal otherSavings;
    String receiptNumber;
    int loyaltyPoints;
    boolean sawLoyaltyPoints;

    boolean hasLines() {
        return !lines.isEmpty();
    }

    /** Call for any line that is not an item or item sub-line. */
    void detach() {
        adjacent = false;
    }

    void addItem(String name, BigDecimal lineTotal, String taxSymbol) {
        addItem(name, lineTotal, taxSymbol, false);
    }

    void addItem(String name, BigDecimal lineTotal, String taxSymbol, boolean discountExcluded) {
        lines.add(new ReceiptLine(name.strip(), BigDecimal.ONE, "Stk", lineTotal, lineTotal, taxSymbol,
                null, null, null, null, discountExcluded));
        adjacent = true;
    }

    /** Applies "n x price" to the current item; false if there is none. */
    boolean multiBuy(int quantity, BigDecimal unitPrice) {
        if (!adjacent) {
            return false;
        }
        int i = lines.size() - 1;
        lines.set(i, lines.get(i).withMultiBuy(BigDecimal.valueOf(quantity), unitPrice));
        return true;
    }

    boolean weight(BigDecimal kg, BigDecimal perKg) {
        if (!adjacent) {
            return false;
        }
        int i = lines.size() - 1;
        lines.set(i, lines.get(i).withWeight(kg, perKg));
        return true;
    }

    /**
     * Records an adjustment with its canonical sign (discounts and returns negative, deposits
     * positive). Discounts and deposits attach to the current item when directly following it.
     */
    void adjust(AdjustmentType type, String description, BigDecimal printedAmount) {
        BigDecimal amount = type == AdjustmentType.PFAND_DEPOSIT ? printedAmount.abs() : printedAmount.abs().negate();
        Integer lineIndex = null;
        if (adjacent && (type.isDiscount() || type == AdjustmentType.PFAND_DEPOSIT)) {
            lineIndex = lines.size() - 1;
            if (type == AdjustmentType.PFAND_DEPOSIT) {
                lines.set(lineIndex, lines.get(lineIndex).withDeposit(amount));
            }
        }
        adjustments.add(new ReceiptAdjustment(type, description, amount, lineIndex));
    }

    /**
     * Applies an "n Stk x p" sub-line to the last adjustment (a multi-unit Pfand or Leergut line):
     * the count goes into the description and, for Pfand, {@code p} becomes the per-unit deposit
     * of the product it is attached to. The amount stays as printed.
     */
    void annotateLastAdjustment(int count, BigDecimal unitAmount, String countText) {
        int i = adjustments.size() - 1;
        ReceiptAdjustment a = adjustments.get(i);
        adjustments.set(i, new ReceiptAdjustment(a.type(), a.description() + " (" + countText + ")",
                a.amount(), a.lineIndex()));
        if (a.type() == AdjustmentType.PFAND_DEPOSIT && a.lineIndex() != null && count > 0) {
            lines.set(a.lineIndex(), lines.get(a.lineIndex()).withDeposit(unitAmount));
        }
    }

    void addTax(TaxBreakdownEntry entry) {
        taxes.add(entry);
    }

    void addLoyaltyPoints(int points) {
        loyaltyPoints += points;
        sawLoyaltyPoints = true;
    }

    void unparsed(String line) {
        unparsed.add(line);
        adjacent = false;
    }

    /** Resolves each line's tax rate from the receipt's own footer table. */
    ParsedReceipt build() {
        Map<String, BigDecimal> rates = new HashMap<>();
        for (TaxBreakdownEntry t : taxes) {
            rates.put(t.symbol(), t.ratePercent());
        }
        List<ReceiptLine> resolved = new ArrayList<>();
        for (ReceiptLine l : lines) {
            resolved.add(l.withTaxRate(rates.get(l.taxSymbol())));
        }
        return new ParsedReceipt(brand, address, storeId, purchasedAt, "EUR", resolved, adjustments,
                total, taxes, savings, sawLoyaltyPoints ? loyaltyPoints : null, unparsed, receiptNumber,
                appSavings, otherSavings);
    }
}
