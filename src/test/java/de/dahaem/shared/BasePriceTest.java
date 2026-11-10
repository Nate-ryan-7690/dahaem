package de.dahaem.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The price per kg / l / piece of a pack, and nothing for units it does not know. */
class BasePriceTest {

    @Test
    void massIsPerKilogramVolumeIsPerLitreCountIsPerPiece() {
        assertEquals(new BasePrice.Base(2.49, "kg"), BasePrice.of(2.49, 1000, "g"));
        assertEquals(new BasePrice.Base(1.78, "l"), BasePrice.of(0.89, 500, "ml"));
        assertEquals(new BasePrice.Base(0.35, "Stück"), BasePrice.of(3.50, 10, "Stück"));
        assertEquals(new BasePrice.Base(4.00, "kg"), BasePrice.of(2.00, 500, "g"));
    }

    @Test
    void aWrongTinyPackShowsUpAsAnAbsurdPricePerKilogram() {
        assertEquals(new BasePrice.Base(2490.0, "kg"), BasePrice.of(2.49, 1, "g"));
    }

    @Test
    void unknownUnitsAndEmptyPacksHaveNoBasePrice() {
        assertNull(BasePrice.of(1.0, 3, "WL"));
        assertNull(BasePrice.of(1.0, 0, "g"));
        assertNull(BasePrice.of(1.0, 500, null));
    }
}
