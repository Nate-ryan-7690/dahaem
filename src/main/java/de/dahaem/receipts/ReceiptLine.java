package de.dahaem.receipts;

import java.math.BigDecimal;

/**
 * One purchasable product. {@code lineTotal} is the printed price before any adjustment; what was
 * actually paid is {@link ParsedReceipt#paidTotal(int)}. {@code taxSymbol} is the receipt's own
 * marker (ALDI: 1/2, PENNY: A/B); {@code taxRatePercent} is resolved from the receipt's footer
 * table and is null if the footer didn't define the symbol. {@code weightKg}/{@code pricePerKg}
 * are set for weighed goods, {@code deposit} (Pfand per unit) for bottles whose deposit line
 * follows; the deposit itself is booked as an adjustment, never in {@code lineTotal}.
 * {@code discountExcluded} is the receipt's own "*" marker after the tax symbol (PENNY: "Keine
 * Rabatte auf die mit * gekennzeichnete Produkte").
 */
public record ReceiptLine(String rawName, BigDecimal quantity, String unit, BigDecimal unitPrice,
                          BigDecimal lineTotal, String taxSymbol, BigDecimal taxRatePercent,
                          BigDecimal weightKg, BigDecimal pricePerKg, BigDecimal deposit,
                          boolean discountExcluded) {

    ReceiptLine withMultiBuy(BigDecimal newQuantity, BigDecimal newUnitPrice) {
        return new ReceiptLine(rawName, newQuantity, unit, newUnitPrice, lineTotal, taxSymbol,
                taxRatePercent, weightKg, pricePerKg, deposit, discountExcluded);
    }

    ReceiptLine withWeight(BigDecimal kg, BigDecimal perKg) {
        return new ReceiptLine(rawName, kg, "kg", perKg, lineTotal, taxSymbol,
                taxRatePercent, kg, perKg, deposit, discountExcluded);
    }

    ReceiptLine withDeposit(BigDecimal newDeposit) {
        return new ReceiptLine(rawName, quantity, unit, unitPrice, lineTotal, taxSymbol,
                taxRatePercent, weightKg, pricePerKg, newDeposit, discountExcluded);
    }

    ReceiptLine withTaxRate(BigDecimal rate) {
        return new ReceiptLine(rawName, quantity, unit, unitPrice, lineTotal, taxSymbol,
                rate, weightKg, pricePerKg, deposit, discountExcluded);
    }
}
