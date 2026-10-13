package de.dahaem.receipts;

/** What kind of price change or deposit movement a receipt line is. */
public enum AdjustmentType {
    POSITIONSRABATT(true),
    APP_RABATT(true),
    LOYALTY_STICKER(true),
    /**
     * PENNY app coupon. A plain "App-Coupon" and a brand coupon ("Ferrero Kinder 25%") are attached to the
     * product before them; a group coupon ("App-Coupon 10% Waren") is not attached to any line.
     */
    APP_COUPON(true),
    /** Reduced-for-quick-sale sticker ("Frischerabatt"). */
    FRESHNESS_DISCOUNT(true),
    /** Pfand charged (positive). Not a discount: it is refunded, not saved. */
    PFAND_DEPOSIT(false),
    /** Empties handed back (negative). */
    LEERGUT_RETURN(false),
    OTHER(true);

    private final boolean discount;

    AdjustmentType(boolean discount) {
        this.discount = discount;
    }

    /** True if this lowers what was paid for a product (as opposed to deposit movements). */
    public boolean isDiscount() {
        return discount;
    }
}
