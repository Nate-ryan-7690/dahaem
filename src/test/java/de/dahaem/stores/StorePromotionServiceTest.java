package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StorePromotionServiceTest {

    @Test
    void gramPackage() {
        StorePromotionService.Pack pack = StorePromotionService.parseSize("500-g-Packung");
        assertEquals(new StorePromotionService.Pack(500, "g"), pack);
    }

    @Test
    void literToMilliliters() {
        StorePromotionService.Pack pack = StorePromotionService.parseSize("1,5-l-Flasche");
        assertEquals(new StorePromotionService.Pack(1500, "ml"), pack);
    }

    @Test
    void multiPackWithLiterUnit() {
        StorePromotionService.Pack pack = StorePromotionService.parseSize("6 x 0,33 l");
        assertEquals(new StorePromotionService.Pack(1980, "ml"), pack);
    }

    @Test
    void noNumberIsUnparseable() {
        assertNull(StorePromotionService.parseSize("Packung"));
    }

    @Test
    void nullInputIsUnparseable() {
        assertNull(StorePromotionService.parseSize(null));
    }

    @Test
    void kilogramConvertsToGrams() {
        StorePromotionService.Pack pack = StorePromotionService.parseSize("2-kg-Sack");
        assertEquals(new StorePromotionService.Pack(2000, "g"), pack);
    }

    @Test
    void centilitersConvertToMilliliters() {
        StorePromotionService.Pack pack = StorePromotionService.parseSize("33-cl-Dose");
        assertEquals(new StorePromotionService.Pack(330, "ml"), pack);
    }

    @Test
    void stueckAbbreviationNormalizesToStueck() {
        StorePromotionService.Pack pack = StorePromotionService.parseSize("10-St.-Packung");
        assertEquals(new StorePromotionService.Pack(10, "stück"), pack);
    }
}
