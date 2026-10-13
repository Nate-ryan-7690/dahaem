package de.dahaem.receipts;

/** What kind of price change or deposit movement a receipt line is. */
public enum AdjustmentType {
    POSITIONSRABATT(true),
    APP_RABATT(true),
    LOYALTY_STICKER(true),
    /** PENNY app coupon ("App-Coupon", "App-Coupon 10% Waren", "Ferrero Kinder 25%"); attached to the product before it. */
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
