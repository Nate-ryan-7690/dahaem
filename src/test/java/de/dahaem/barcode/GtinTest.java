package de.dahaem.barcode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Typed barcode numbers: forms, length, check digit. */
class GtinTest {

    @Test
    void acceptsEan13AndStripsSpacesAndHyphens() {
        assertEquals("4006381333931", Gtin.normalize("4006381333931"));
        assertEquals("4006381333931", Gtin.normalize(" 4006 3813 33931 "));
        assertEquals("4006381333931", Gtin.normalize("4006381-333931"));
    }

    @Test
    void aTwelveDigitUpcIsTheSameProductAsTheThirteenDigitFormWithALeadingZero() {
        assertEquals("0036000291452", Gtin.normalize("036000291452"));
        assertEquals("0036000291452", Gtin.normalize("0036000291452"));
    }

    @Test
    void acceptsEan8() {
        assertEquals("73513537", Gtin.normalize("73513537"));
    }

    @Test
    void aWrongCheckDigitIsATypingErrorWithItsOwnCode() {
        InvalidBarcodeException e = assertThrows(InvalidBarcodeException.class, () -> Gtin.normalize("4006381333932"));
        assertEquals("BARCODE_CHECKSUM", e.code());
    }

    @Test
    void wrongLengthAndNonDigitsAreInvalid() {
        for (String bad : new String[]{"1234567890", "123", "40063813339a1", "", "   ", null}) {
            InvalidBarcodeException e = assertThrows(InvalidBarcodeException.class, () -> Gtin.normalize(bad), String.valueOf(bad));
            assertEquals("INVALID_BARCODE", e.code(), String.valueOf(bad));
        }
    }
}
