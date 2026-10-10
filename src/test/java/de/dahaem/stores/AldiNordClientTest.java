package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AldiNordClientTest {

    @Test
    void aContainerWordOnlyPackSizeIsDerivedFromThePricePerKilo() {
        // CUCINA Pizza Neapolitan Style, TRADER JOE'S Stuffed-Crust-Pizza, TRADER JOE'S Fully-Loaded-Pizza
        assertEquals("380-g-Packung", AldiNordClient.deriveSalesUnit("Packung", 2.99, 7.85, "kg"));
        assertEquals("455-g-Packung", AldiNordClient.deriveSalesUnit("Packung", 2.99, 6.57, "kg"));
        assertEquals("505-g-Packung", AldiNordClient.deriveSalesUnit("Packung", 2.79, 5.52, "kg"));
    }

    @Test
    void litresAndLargePacksAreDerivedToo() {
        assertEquals("750-ml-Flasche", AldiNordClient.deriveSalesUnit("Flasche", 1.50, 2.00, "l"));
        assertEquals("1500-g-Beutel", AldiNordClient.deriveSalesUnit("Beutel", 3.00, 2.00, "kg"));
    }

    @Test
    void aStatedQuantityOrMissingBaseDataLeavesThePackSizeAlone() {
        assertEquals("270-g-Packung", AldiNordClient.deriveSalesUnit("270-g-Packung", 1.99, 7.37, "kg"));
        assertEquals("Packung", AldiNordClient.deriveSalesUnit("Packung", 2.99, null, "kg"));
        assertEquals("Packung", AldiNordClient.deriveSalesUnit("Packung", 2.99, 7.85, "Stück"));
        assertEquals("Stück", AldiNordClient.deriveSalesUnit("Stück", 2.99, 7.85, "kg"));
    }
}
