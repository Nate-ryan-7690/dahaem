package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizerTest {

    @Test
    void lowercasesAndTrims() {
        assertEquals("butter", Normalizer.key("  Butter  "));
    }

    @Test
    void convertsUmlautsAndEszett() {
        assertEquals("kaese", Normalizer.key("Käse"));
        assertEquals("oel", Normalizer.key("Öl"));
        assertEquals("gruen", Normalizer.key("Grün"));
        assertEquals("suess", Normalizer.key("süß"));
    }

    @Test
    void stripsLeadingAllCapsBrandWord() {
        assertEquals("streichfein", Normalizer.key("MILSANI Streichfein"));
    }

    @Test
    void keepsAllWordsWhenEveryWordIsAllCaps() {
        // stripBrand never drops the last word, so an all-caps title survives intact.
        assertEquals("milsani", Normalizer.key("MILSANI"));
    }

    @Test
    void stripsMultipleLeadingBrandWords() {
        assertEquals("streichfein ungesalzen", Normalizer.key("MILSANI GOLD Streichfein ungesalzen"));
    }

    @Test
    void removesAllergenMarkers() {
        assertEquals("nussmix", Normalizer.key("Nussmix 5)"));
    }

    @Test
    void removesOriginCodes() {
        assertEquals("tomaten", Normalizer.key("Tomaten DE"));
        assertEquals("paprika", Normalizer.key("Paprika DE | NL"));
    }

    @Test
    void removesPunctuationAndCollapsesSpaces() {
        assertEquals("bio rapsoel", Normalizer.key("Bio-Rapsöl,  **"));
    }

    @Test
    void nullInputYieldsEmptyString() {
        assertEquals("", Normalizer.key(null));
    }

    @Test
    void stripBrandKeepsNonCapsTitleUnchanged() {
        assertEquals("Frische Vollmilch", Normalizer.stripBrand("Frische Vollmilch"));
    }
}
