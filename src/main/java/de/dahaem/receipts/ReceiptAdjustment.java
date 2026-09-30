package de.dahaem.receipts;

import java.math.BigDecimal;

/**
 * A discount or deposit movement, kept apart from products (paid price != listed price != offer;
 * deposit != discount). {@code amount} is signed as it affects the total: discounts and returns
 * negative, deposits positive. {@code lineIndex} is the product line it attaches to, or null when
 * that can't be determined.
 */
public record ReceiptAdjustment(AdjustmentType type, String description, BigDecimal amount, Integer lineIndex) {
}
