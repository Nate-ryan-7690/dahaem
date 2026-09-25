package de.dahaem.finance;

import java.time.LocalDate;

/**
 * Plain data holder for one row of the purchase_history table.
 * No logic here on purpose: just the fields, a constructor, and getters.
 */
public class Purchase {

    private final int id;
    private final int storeItemId;
    private final Integer discountId;      // may be null
    private final boolean wasDiscounted;
    private final LocalDate purchaseDate;
    private final int quantityPurchased;
    private final double pricePaid;
    private final LocalDate expirationDate;

    public Purchase(int id, int storeItemId, Integer discountId, boolean wasDiscounted,
                     LocalDate purchaseDate, int quantityPurchased, double pricePaid, LocalDate expirationDate) {
        this.id = id;
        this.storeItemId = storeItemId;
        this.discountId = discountId;
        this.wasDiscounted = wasDiscounted;
        this.purchaseDate = purchaseDate;
        this.quantityPurchased = quantityPurchased;
        this.pricePaid = pricePaid;
        this.expirationDate = expirationDate;
    }

    public int getId() {
        return id;
    }

    public int getStoreItemId() {
        return storeItemId;
    }

    public Integer getDiscountId() {
        return discountId;
    }

    public boolean isWasDiscounted() {
        return wasDiscounted;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public int getQuantityPurchased() {
        return quantityPurchased;
    }

    public double getPricePaid() {
        return pricePaid;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    @Override
    public String toString() {
        return "Purchase{" +
                "id=" + id +
                ", storeItemId=" + storeItemId +
                ", discountId=" + discountId +
                ", wasDiscounted=" + wasDiscounted +
                ", purchaseDate=" + purchaseDate +
                ", quantityPurchased=" + quantityPurchased +
                ", pricePaid=" + pricePaid +
                ", expirationDate=" + expirationDate +
                '}';
    }
}
