package de.dahaem.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortionsTest {

    @Test
    void scalesByPeopleOverServings() {
        assertEquals(800.0, Portions.scale(400, 4, 2));
        assertEquals(1.5, Portions.scale(1, 3, 2));
        assertEquals(0.5, Portions.scale(1, 1, 2));
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertEquals(0.33, Portions.scaleRounded(1, 1, 3));
        assertEquals(0.67, Portions.scaleRounded(1, 2, 3));
        assertEquals(266.67, Portions.scaleRounded(400, 2, 3));
    }

    @Test
    void nonPositiveServingsAreNotScaled() {
        assertFalse(Portions.isScalable(0));
        assertFalse(Portions.isScalable(-1));
        assertTrue(Portions.isScalable(1));
        assertEquals(300.0, Portions.scale(300, 4, 0));
    }

    @Test
    void peopleBelowOneIsRejected() {
        assertFalse(Portions.isValidPeople(0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Portions.scale(1, 0, 2));
        assertEquals("'people' must be at least 1", e.getMessage());
    }
}
