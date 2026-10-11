package de.dahaem.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortionsTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void scalesByPeopleOverServings() {
        assertEquals(d("800.00"), Portions.scaleRounded(d("400"), 4, 2));
        assertEquals(d("1.50"), Portions.scaleRounded(d("1"), 3, 2));
        assertEquals(d("0.50"), Portions.scaleRounded(d("1"), 1, 2));
    }

    @Test
    void scalesDecimalQuantities() {
        assertEquals(d("1.00"), Portions.scaleRounded(d("0.5"), 4, 2));      // ½ Stück, 2 -> 4 people
        assertEquals(d("0.75"), Portions.scaleRounded(d("0.5"), 3, 2));
        assertEquals(d("0.50"), Portions.scaleRounded(d("0.333"), 3, 2));    // 0.4995 rounds once, at the end
        assertEquals(d("1.33"), Portions.scaleRounded(d("0.667"), 4, 2));
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertEquals(d("0.33"), Portions.scaleRounded(d("1"), 1, 3));
        assertEquals(d("0.67"), Portions.scaleRounded(d("1"), 2, 3));
        assertEquals(d("266.67"), Portions.scaleRounded(d("400"), 2, 3));
    }

    @Test
    void nonPositiveServingsAreNotScaled() {
        assertFalse(Portions.isScalable(0));
        assertFalse(Portions.isScalable(-1));
        assertTrue(Portions.isScalable(1));
        assertEquals(d("300"), Portions.scaleRounded(d("300"), 4, 0));
    }

    @Test
    void rangeScalesFromLowerBoundUnlessMaxIsAsked() {
        assertEquals(3, Portions.scalingServings(3, 4, false));
        assertEquals(4, Portions.scalingServings(3, 4, true));
        assertEquals(2, Portions.scalingServings(2, null, true), "basis max is ignored when not a range");
        assertEquals(d("0.67"), Portions.scaleRounded(d("1"), 2, Portions.scalingServings(3, 4, false)));
        assertEquals(d("0.50"), Portions.scaleRounded(d("1"), 2, Portions.scalingServings(3, 4, true)));
    }

    @Test
    void peopleBelowOneIsRejected() {
        assertFalse(Portions.isValidPeople(0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Portions.scaleRounded(d("1"), 0, 2));
        assertEquals("'people' must be at least 1", e.getMessage());
    }
}
