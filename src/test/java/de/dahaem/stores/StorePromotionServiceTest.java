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

    // ---- WL, multipacks, ranges (lower number), broth yields ----

    @Test
    void washLoadsAreACountUnit() {
        assertEquals(new StorePromotionService.Pack(71, "WL"), StorePromotionService.parseSize("71WL"));
        assertEquals(new StorePromotionService.Pack(45, "WL"), StorePromotionService.parseSize("45 WL"));
        assertEquals(new StorePromotionService.Pack(1, "WL"), StorePromotionService.parseSize("1 WL"));
    }

    @Test
    void multipackCountsAreThatManyPieces() {
        assertEquals(new StorePromotionService.Pack(8, "stück"), StorePromotionService.parseSize("8er-Packung"));
        assertEquals(new StorePromotionService.Pack(10, "stück"), StorePromotionService.parseSize("10er"));
        assertEquals(new StorePromotionService.Pack(6, "stück"), StorePromotionService.parseSize("6er-Pack"));
        assertEquals(new StorePromotionService.Pack(6, "stück"), StorePromotionService.parseSize("6er-XXL-Packung"));
        assertNull(StorePromotionService.parseSize("2er-, 3er- oder 5er-Packung"), "several counts: ambiguous");
        // an explicit quantity still wins and the existing multiplier forms keep working
        assertEquals(new StorePromotionService.Pack(240, "ml"), StorePromotionService.parseSize("12 x 20 ml"));
        assertEquals(new StorePromotionService.Pack(480, "ml"), StorePromotionService.parseSize("24 x 0,02l"));
        assertEquals(new StorePromotionService.Pack(250, "g"), StorePromotionService.parseSize("5x50-g-Packung"));
    }

    @Test
    void aRangeUsesTheLowerNumber() {
        assertEquals(new StorePromotionService.Pack(46, "WL"), StorePromotionService.parseSize("46-50 WL"));
        assertEquals(new StorePromotionService.Pack(4, "stück"), StorePromotionService.parseSize("4 - 6 Stück"));
        assertEquals(new StorePromotionService.Pack(6, "stück"), StorePromotionService.parseSize("6 - 9 Stück"));
        assertEquals(new StorePromotionService.Pack(140, "g"), StorePromotionService.parseSize("140 - 150g"));
        assertEquals(new StorePromotionService.Pack(500, "ml"), StorePromotionService.parseSize("0,5 - 0,7l"));
        assertEquals(new StorePromotionService.Pack(750, "g"), StorePromotionService.parseSize("0,75 - 1kg"));
        assertEquals(new StorePromotionService.Pack(248, "ml"), StorePromotionService.parseSize("5 x 49,5 - 50,3ml"));
        // not ranges: a container with the size after the hyphen
        assertEquals(new StorePromotionService.Pack(5000, "g"), StorePromotionService.parseSize("5-kg-Netz"));
        assertEquals(new StorePromotionService.Pack(150, "g"), StorePromotionService.parseSize("150-g-Becher"));
    }

    @Test
    void aLitreFigureOnABrothIsAYieldNotTheContent() {
        assertNull(StorePromotionService.parseSize("18 I", "Maggi Gemüse Brühe"));
        assertNull(StorePromotionService.parseSize("18 l", "MAGGI Gemüse Brühe"));
        assertNull(StorePromotionService.parseSize("12 l", "Knorr Bouillon"));
        assertEquals(new StorePromotionService.Pack(300, "g"), StorePromotionService.parseSize("300 g für 12 l", "Knorr Fond"));
        assertEquals(new StorePromotionService.Pack(1000, "ml"), StorePromotionService.parseSize("1 l", "Milch"));
    }
}
