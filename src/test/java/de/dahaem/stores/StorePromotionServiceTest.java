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

    @Test
    void dotBeforeThreeDigitsIsAGermanThousandsSeparatorForSmallUnits() {
        StorePromotionService.Pack pack = StorePromotionService.parseSize("1.062-ml-Glas");

        assertEquals(1062, pack.size());
        assertEquals("ml", pack.unit());
    }

    @Test
    void dotBeforeThreeDigitsStaysADecimalForKgAndLitres() {
        assertEquals(1250, StorePromotionService.parseSize("1.250 kg").size());
        assertEquals(1500, StorePromotionService.parseSize("1.5 l").size());
    }

    @Test
    void capitalIReadAsLitreOnlyDirectlyAfterANumber() {
        assertEquals(700, StorePromotionService.parseSize("0,7 I").size());
        assertEquals("ml", StorePromotionService.parseSize("0,7 I").unit());
        assertEquals(1500, StorePromotionService.parseSize("1,5 I").size());
        assertEquals(1000, StorePromotionService.parseSize("1 I").size());
        assertEquals(500, StorePromotionService.parseSize("0,5-I-Dose").size());
        assertEquals(2000, StorePromotionService.parseSize("2 I-Flasche").size());
    }

    @Test
    void capitalIInOtherWordsIsNotALitre() {
        assertNull(StorePromotionService.parseSize("Typ I"));
        assertNull(StorePromotionService.parseSize("Größe I"));
        assertNull(StorePromotionService.parseSize("2 IN 1"));
        assertNull(StorePromotionService.parseSize("3 Impfstoffe"));
        assertEquals(500, StorePromotionService.parseSize("500 g I").size());
        assertEquals("g", StorePromotionService.parseSize("500 g I").unit());
    }
}
