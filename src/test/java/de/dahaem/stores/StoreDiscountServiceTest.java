package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreDiscountServiceTest {

    private static final Map<String, String> KNOWN = Map.of("iglo", "IGLO", "oro", "ORO");

    @Test
    void unbrandedSingleWordNameHasNoBrandAndMatchesOnTheFullName() {
        StoreDiscountService.BrandSplit split = StoreDiscountService.resolveBrand(null, "Fischstäbchen", KNOWN);

        assertNull(split.brand());
        assertEquals("Fischstäbchen", split.matchName());
    }

    @Test
    void unbrandedMultiWordNameWithoutKnownBrandKeepsItsFullName() {
        StoreDiscountService.BrandSplit split = StoreDiscountService.resolveBrand("", "Frische Zwiebeln", KNOWN);

        assertNull(split.brand());
        assertEquals("Frische Zwiebeln", split.matchName());
    }

    @Test
    void brandlessNameStartingWithAKnownBrandYieldsThatBrand() {
        StoreDiscountService.BrandSplit split = StoreDiscountService.resolveBrand(null, "Iglo Filegro", KNOWN);

        assertEquals("IGLO", split.brand());
        assertEquals("Filegro", split.matchName());
    }

    @Test
    void aBrandFromTheFlyerIsKeptAsIs() {
        StoreDiscountService.BrandSplit split = StoreDiscountService.resolveBrand("MÜHLENHOF", "Frisches Hackfleisch", KNOWN);

        assertEquals("MÜHLENHOF", split.brand());
        assertEquals("Frisches Hackfleisch", split.matchName());
    }
}
