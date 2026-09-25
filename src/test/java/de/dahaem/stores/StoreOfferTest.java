package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoreOfferTest {

    private static StoreOffer offer(double salePrice, int dealQuantity, Double dealPrice, Double deposit) {
        return new StoreOffer("ext-1", "Brand", "Name", "500 g", salePrice, dealQuantity, dealPrice,
                null, null, null, deposit, null, "", "", LocalDate.now(), LocalDate.now());
    }

    @Test
    void singleUnitWithoutDeposit() {
        StoreOffer o = offer(1.99, 1, null, null);
        assertEquals(1.99, o.tillPrice(), 1e-9);
    }

    @Test
    void singleUnitWithDepositAddsIt() {
        StoreOffer o = offer(0.89, 1, null, 0.25);
        assertEquals(1.14, o.tillPrice(), 1e-9);
    }

    @Test
    void multiBuyUsesDealPriceNotSalePriceTimesQuantity() {
        // "3 for 5 €": dealPrice is the total, not salePrice * dealQuantity.
        StoreOffer o = offer(2.00, 3, 5.00, null);
        assertEquals(5.00, o.tillPrice(), 1e-9);
    }

    @Test
    void multiBuyWithDepositMultipliesDepositByQuantity() {
        StoreOffer o = offer(0.79, 6, 4.50, 0.25);
        assertEquals(4.50 + 0.25 * 6, o.tillPrice(), 1e-9);
    }

    @Test
    void noDealPriceFallsBackToSalePriceTimesQuantity() {
        StoreOffer o = offer(1.50, 2, null, null);
        assertEquals(3.00, o.tillPrice(), 1e-9);
    }
}
