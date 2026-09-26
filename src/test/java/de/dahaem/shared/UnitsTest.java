package de.dahaem.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitsTest {

    @ParameterizedTest
    @CsvSource({
            "g,MASS,1.0",
            "gramm,MASS,1.0",
            "GRAMM,MASS,1.0",
            "kg,MASS,1000.0",
            "KG,MASS,1000.0",
            "ml,VOLUME,1.0",
            "cl,VOLUME,10.0",
            "l,VOLUME,1000.0",
            "Liter,VOLUME,1000.0",
            "Stück,COUNT,1.0",
            "stk,COUNT,1.0",
            "St,COUNT,1.0",
            "pcs,COUNT,1.0",
            "Packung,COUNT,1.0",
            "Pack,COUNT,1.0",
            "Dose,COUNT,1.0",
            "Bund,COUNT,1.0",
            "Glas,COUNT,1.0",
    })
    void parsesKnownUnitsCaseInsensitivelyIntoDimensionAndBaseFactor(String raw, String dimension, double factor) {
        Units.Parsed parsed = Units.parse(raw);
        assertEquals(Units.Dimension.valueOf(dimension), parsed.dimension());
        assertEquals(factor, parsed.toBaseFactor(), 0.0001);
        assertTrue(parsed.isKnown());
    }

    @Test
    void toleratesTrailingJunkAndWhitespace() {
        assertEquals(Units.Dimension.COUNT, Units.parse("Stück.").dimension());
        assertEquals(Units.Dimension.COUNT, Units.parse("  Stück  ").dimension());
        assertEquals(Units.Dimension.MASS, Units.parse(" g ").dimension());
    }

    @Test
    void unrecognizedUnitIsUnknown() {
        Units.Parsed parsed = Units.parse("Bogus");
        assertEquals(Units.Dimension.UNKNOWN, parsed.dimension());
        assertFalse(parsed.isKnown());
    }

    @Test
    void nullOrBlankUnitIsUnknown() {
        assertFalse(Units.parse(null).isKnown());
        assertFalse(Units.parse("").isKnown());
        assertFalse(Units.parse("   ").isKnown());
    }

    @Test
    void compatibilityMatrixOnlyMatchesSameDimension() {
        assertTrue(Units.compatible("g", "kg"));
        assertTrue(Units.compatible("kg", "g"));
        assertTrue(Units.compatible("ml", "l"));
        assertTrue(Units.compatible("cl", "l"));
        assertTrue(Units.compatible("Stück", "Packung"));
        assertTrue(Units.compatible("Dose", "Stk"));

        assertFalse(Units.compatible("g", "ml"), "mass and volume are never compatible");
        assertFalse(Units.compatible("g", "Stück"), "mass and count are never compatible");
        assertFalse(Units.compatible("ml", "Stück"), "volume and count are never compatible");
        assertFalse(Units.compatible("g", "Bogus"), "an unknown unit is never compatible with anything");
        assertFalse(Units.compatible("Bogus", "Bogus2"), "two unknown units are never compatible");
    }

    @Test
    void toBaseConvertsUsingTheDimensionsFactor() {
        assertEquals(2400.0, Units.toBase(2400, "g"), 0.0001);
        assertEquals(2.4, Units.toBase(2400, "g") / 1000.0, 0.0001);
        assertEquals(2000.0, Units.toBase(2, "kg"), 0.0001);
        assertEquals(250.0, Units.toBase(250, "ml"), 0.0001);
        assertEquals(1000.0, Units.toBase(1, "l"), 0.0001);
        assertEquals(10.0, Units.toBase(10, "Stück"), 0.0001);
    }

    @Test
    void toBaseIsNullForAnUnknownUnit() {
        assertNull(Units.toBase(100, "Bogus"));
        assertNull(Units.toBase(100, null));
    }
}
