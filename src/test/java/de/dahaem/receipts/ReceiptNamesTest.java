package de.dahaem.receipts;

import de.dahaem.stores.Normalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Receipt-specific name handling: CAPS words, PENNY abbreviations, known brand prefixes. Made-up names only. */
class ReceiptNamesTest {

    @Test
    void capsWordsAreNotReadAsBrandWords() {
        assertEquals("3 5", Normalizer.key("ESL MILCH 3,5%"), "the offer normalizer strips caps words (unchanged)");
        assertEquals("Esl Milch 3,5%", ReceiptNames.readable("ESL MILCH 3,5%"));
        assertEquals("esl milch 3 5", ReceiptNames.key("ESL MILCH 3,5%"));
        assertEquals("cheddar herzhaft", ReceiptNames.key("CHEDDAR HERZHAFT"));
        assertEquals("w c mayo", ReceiptNames.key("W&C Mayo"));
    }

    @Test
    void mixedCaseNamesStayAsPrinted() {
        assertEquals("Bagels Mehrkorn", ReceiptNames.readable("Bagels Mehrkorn"));
        assertEquals("Ben & Jerry's", ReceiptNames.readable("Ben & Jerry┤s"));
    }

    @Test
    void pennyAbbreviationsAreExpanded() {
        assertEquals("Hackfleisch gemischt", ReceiptNames.readable("Hackfleisch gem."));
        assertEquals("Hackfleisch gemischt", ReceiptNames.readable("HACKFLEISCH GEM."));
        assertEquals("Hähnchen-Brustfilet", ReceiptNames.readable("Hä.-Brustfilet"));
        assertEquals("Landliebe Konfitüre", ReceiptNames.readable("Landliebe Konfit"));
        assertEquals("Sf Passierte Tomaten", ReceiptNames.readable("SF Passierte Tom"));
        assertEquals("haehnchen brustfilet", ReceiptNames.key("Hä.-Brustfilet"));
    }

    @Test
    void abbreviationsOnlyMatchWholeWords() {
        assertEquals("Konfitüre", ReceiptNames.readable("Konfitüre"));
        assertEquals("Tomaten Mark", ReceiptNames.readable("TOMATEN MARK"));
        assertEquals("Gemüse", ReceiptNames.readable("Gemüse"));
    }

    @Test
    void brandOnlyFromAKnownPrefix() {
        assertEquals("Lindenhof", ReceiptNames.brand("LH FRISCHK.NATUR"));
        assertEquals("FRISCHK.NATUR", ReceiptNames.withoutBrand("LH FRISCHK.NATUR"));
        assertEquals("Lay's", ReceiptNames.brand("LAYS GESALZEN"));
        assertEquals("Landliebe", ReceiptNames.brand("Landliebe Konfit"));
        assertEquals("W&C", ReceiptNames.brand("W&C Mayo"));
        assertEquals("Krombacher", ReceiptNames.brand("Krom FB Kirsche"));
        assertEquals("Kerrygold", ReceiptNames.brand("KERRYGOLD CHEDD."));
        assertNull(ReceiptNames.brand("Hackfleisch gem."));
        assertNull(ReceiptNames.brand("ESL MILCH 3,5%"));
        assertNull(ReceiptNames.brand("LHASA TEE"), "a prefix must be a whole word");
        assertEquals("ESL MILCH 3,5%", ReceiptNames.withoutBrand("ESL MILCH 3,5%"));
    }
}
