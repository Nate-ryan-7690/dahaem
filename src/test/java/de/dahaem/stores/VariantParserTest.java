package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantParserTest {

    @Test
    void parsesBulletsWithSize() {
        String description = "Sorten:\n\n * Paprika-Kräuter, 125 g\n * Natur, 150 g";
        List<ProductVariant> variants = VariantParser.parse(description);
        assertEquals(2, variants.size());
        assertEquals(new ProductVariant("Paprika-Kräuter", "125 g"), variants.get(0));
        assertEquals(new ProductVariant("Natur", "150 g"), variants.get(1));
    }

    @Test
    void bulletWithoutSizeKeepsSizeNull() {
        String description = "Sorten:\n * Nur ein Name ohne Groesse";
        List<ProductVariant> variants = VariantParser.parse(description);
        assertEquals(1, variants.size());
        assertEquals("Nur ein Name ohne Groesse", variants.get(0).name());
        assertEquals(null, variants.get(0).size());
    }

    @Test
    void noSortenBlockYieldsEmptyList() {
        assertTrue(VariantParser.parse("Just a regular description, 500 g").isEmpty());
        assertTrue(VariantParser.parse("").isEmpty());
    }

    @Test
    void nullDescriptionYieldsEmptyList() {
        assertTrue(VariantParser.parse(null).isEmpty());
    }

    @Test
    void blockEndsAtFirstNonBulletLine() {
        String description = "Sorten:\n * Natur, 150 g\nSome trailing text\n * Ignored, 200 g";
        List<ProductVariant> variants = VariantParser.parse(description);
        assertEquals(1, variants.size());
        assertEquals(new ProductVariant("Natur", "150 g"), variants.get(0));
    }

    @Test
    void headerCanUseDashBulletsAndSingularSorte() {
        String description = "**Sorte:**\n- Klassik, 330 ml";
        List<ProductVariant> variants = VariantParser.parse(description);
        assertEquals(1, variants.size());
        assertEquals(new ProductVariant("Klassik", "330 ml"), variants.get(0));
    }

    @Test
    void multiPackSizeWithInternalCommaSplitsOnWrongComma() {
        // KNOWN LIMITATION (not fixed here, see PR description): the parser splits on the
        // *last* comma in the bullet line. A multi-pack size written as "6 x 0,33 l" has its
        // own comma (German decimal separator), so lastIndexOf(',') lands inside the size
        // itself and the name/size split comes out wrong. Documented via this test rather
        // than silently asserting broken behavior as "correct".
        String description = "Sorten:\n * Klassik, 6 x 0,33 l";
        List<ProductVariant> variants = VariantParser.parse(description);
        assertEquals(1, variants.size());
        assertEquals(new ProductVariant("Klassik, 6 x 0", "33 l"), variants.get(0));
    }

    @Test
    void commaWithoutTrailingSizeStaysInName() {
        // The last comma's tail must look like a size, otherwise it's part of the name.
        String description = "Sorten:\n * Gerstenmalz, gemahlen";
        List<ProductVariant> variants = VariantParser.parse(description);
        assertEquals(1, variants.size());
        assertEquals("Gerstenmalz, gemahlen", variants.get(0).name());
        assertEquals(null, variants.get(0).size());
    }
}
