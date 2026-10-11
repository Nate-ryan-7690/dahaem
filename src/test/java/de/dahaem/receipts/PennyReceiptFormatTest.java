package de.dahaem.receipts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PENNY layout details as printed by {@code pdftotext -layout}: discount-excluded "*" items,
 * one-line deposit returns, Pfand amounts other than 0,25, and the header/footer/card block.
 * All names, numbers, IDs and signatures are made up; only the layout follows real receipts.
 */
class PennyReceiptFormatTest {

    private final ReceiptParser parser = new ReceiptParser();

    private ParsedReceipt penny(String text) {
        return parser.parse(text, StoreFormat.PENNY);
    }

    private static String fixture(String name) {
        try (InputStream in = PennyReceiptFormatTest.class.getResourceAsStream("/receipts/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertNum(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static int indexOf(ParsedReceipt r, String name) {
        for (int i = 0; i < r.lines().size(); i++) {
            if (r.lines().get(i).rawName().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("no line " + name);
    }

    private static List<ReceiptAdjustment> returns(ParsedReceipt r) {
        return r.adjustments().stream().filter(a -> a.type() == AdjustmentType.LEERGUT_RETURN).toList();
    }

    // ---- gap 1: "*" marks a product excluded from discounts ----------------------------------

    @Test
    void discountExcludedItemsAreParsedAndKeepTheMarker() {
        ParsedReceipt r = penny("""
                     DUSCHTUCH BLAU                  24,99 A *
                     KINDERPONCHO                    29,99 A *
                     BUCH FREUNDE GEF                2,99 B *
                     BROT                            1,89 B
                     SUMME                    EUR    59,86
                """);
        assertEquals(4, r.lines().size());
        assertTrue(r.unparsedLines().isEmpty(), r.unparsedLines().toString());

        ReceiptLine poncho = r.lines().get(indexOf(r, "KINDERPONCHO"));
        assertNum("29.99", poncho.lineTotal());
        assertEquals("A", poncho.taxSymbol());
        assertTrue(poncho.discountExcluded());
        ReceiptLine buch = r.lines().get(indexOf(r, "BUCH FREUNDE GEF"));
        assertEquals("B", buch.taxSymbol());
        assertTrue(buch.discountExcluded());
        assertFalse(r.lines().get(indexOf(r, "BROT")).discountExcluded());
        assertTrue(r.balances());
    }

    // ---- gap 2: deposit returns on one line, optional count line after ------------------------

    @Test
    void oneLineDepositReturnsWithAndWithoutCountLine() {
        ParsedReceipt r = penny("""
                     LEERGUT EINWEG                  -0,75 A *
                                   3 Stk x 0,25
                     LEERGUT EINWEG                  -3,25 A *
                     LEERG. EW E. ST                 -0,50 B *
                                   2 Stk x 0,25
                     SUMME                    EUR    -4,50
                """);
        assertTrue(r.lines().isEmpty(), "deposit returns are not products");
        assertTrue(r.unparsedLines().isEmpty(), r.unparsedLines().toString());

        List<ReceiptAdjustment> ret = returns(r);
        assertEquals(3, ret.size());
        assertNum("-0.75", ret.get(0).amount());
        assertEquals("LEERGUT EINWEG (3 Stk x 0,25)", ret.get(0).description());
        assertNum("-3.25", ret.get(1).amount());
        assertEquals("LEERGUT EINWEG", ret.get(1).description());
        assertNum("-0.50", ret.get(2).amount());
        assertEquals("LEERG. EW E. ST (2 Stk x 0,25)", ret.get(2).description());
        assertTrue(ret.stream().allMatch(a -> a.lineIndex() == null && !a.type().isDiscount()));
        assertTrue(r.balances());
    }

    @Test
    void returnCountLineDoesNotChangeThePrecedingProduct() {
        ParsedReceipt r = penny("""
                     BROT                            1,89 B
                     LEERGUT EINWEG                  -0,75 A *
                                   3 Stk x 0,25
                     SUMME                    EUR     1,14
                """);
        ReceiptLine brot = r.lines().get(0);
        assertNum("1", brot.quantity());
        assertNum("1.89", brot.unitPrice());
        assertTrue(r.balances());
    }

    // ---- gap 3: Pfand of any amount, EUR or EURO ---------------------------------------------

    @Test
    void depositOfAnyAmountAttachesToThePrecedingProduct() {
        ParsedReceipt r = penny("""
                     WASSER                          0,19 A
                     PFAND 0,25 EURO                  0,25 A *
                     SCHORLE                         1,29 A
                     PFAND 0,32 EUR                   0,32 A *
                     BIER                            0,89 A
                     PFAND 0,48 EUR                   0,48 A *
                     SUMME                    EUR     3,42
                """);
        assertEquals(3, r.lines().size());
        assertTrue(r.unparsedLines().isEmpty(), r.unparsedLines().toString());
        assertNum("0.25", r.lines().get(indexOf(r, "WASSER")).deposit());
        assertNum("0.32", r.lines().get(indexOf(r, "SCHORLE")).deposit());
        assertNum("0.48", r.lines().get(indexOf(r, "BIER")).deposit());

        int bier = indexOf(r, "BIER");
        ReceiptAdjustment pfand = r.adjustmentsFor(bier).get(0);
        assertEquals(AdjustmentType.PFAND_DEPOSIT, pfand.type());
        assertEquals("PFAND 0,48 EUR", pfand.description());
        assertNum("0.48", pfand.amount());
        assertNum("0.89", r.paidTotal(bier)); // deposit is not part of the paid price
        assertTrue(r.balances());
    }

    @Test
    void multiUnitDepositKeepsProductQuantityAndPerUnitDeposit() {
        ParsedReceipt r = penny("""
                     MINERALWASSER                   0,98 A
                                   2 Stk x 0,49
                     PFAND 0,25 EURO                  0,50 A *
                                   2 Stk x 0,25
                     SUMME                    EUR     1,48
                """);
        ReceiptLine wasser = r.lines().get(0);
        assertNum("2", wasser.quantity());
        assertNum("0.49", wasser.unitPrice());
        assertNum("0.25", wasser.deposit());
        assertNum("0.50", r.adjustmentsFor(0).get(0).amount());
        assertTrue(r.unparsedLines().isEmpty(), r.unparsedLines().toString());
        assertTrue(r.balances());
    }

    // ---- gap 4: header/footer recognised, footer values extracted ----------------------------

    @Test
    void headerFooterAndCardBlockAreRecognisedNotUnparsed() {
        ParsedReceipt r = penny(fixture("penny_made_up_full.txt"));
        assertEquals(List.of(), r.unparsedLines());
        assertEquals("Musterweg 12, 12345 Beispielstadt", r.storeAddress());
        assertEquals(LocalDateTime.of(2026, 10, 3, 17, 42), r.purchasedAt());
        assertEquals(2, r.taxBreakdown().size());
    }

    @Test
    void footerSavingsLoyaltyPointsAndReceiptNumberAreExtracted() {
        ParsedReceipt r = penny(fixture("penny_made_up_full.txt"));
        assertNum("0.20", r.appSavings());
        assertNum("22.00", r.otherSavings());
        assertNum("22.20", r.totalSavings());
        assertEquals(3, r.loyaltyPointsEarned());
        assertEquals("4711", r.receiptNumber());
    }

    @Test
    void footerValuesAreNullWhenNotPrinted() {
        ParsedReceipt r = penny("BROT  1,89 B\nSUMME  EUR  1,89");
        assertNull(r.appSavings());
        assertNull(r.otherSavings());
        assertNull(r.totalSavings());
        assertNull(r.loyaltyPointsEarned());
        assertNull(r.receiptNumber());
    }

    // ---- gap 5: full receipt balances --------------------------------------------------------

    @Test
    void fullMadeUpReceiptBalancesToTheCent() {
        String text = fixture("penny_made_up_full.txt");
        assertEquals(StoreFormat.PENNY, parser.detectFormat(text));
        ParsedReceipt r = parser.parse(text, parser.detectFormat(text));

        assertEquals(7, r.lines().size());
        assertNum("36.92", r.total());
        assertNum("36.92", r.computedTotal());
        assertNum("0", r.delta());
        assertTrue(r.balances());

        assertEquals(3, returns(r).size());
        assertNum("-4.50", returns(r).stream().map(ReceiptAdjustment::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(3, r.adjustments().stream().filter(a -> a.type() == AdjustmentType.PFAND_DEPOSIT).count());
        assertNum("-0.20", r.adjustmentsFor(indexOf(r, "MILCH")).get(0).amount());
        assertNum("2.99", r.paidTotal(indexOf(r, "DUSCHTUCH BLAU")));
        assertTrue(r.lines().get(indexOf(r, "DUSCHTUCH BLAU")).discountExcluded());
        assertNum("19.00", r.lines().get(indexOf(r, "KINDERPONCHO")).taxRatePercent());
        assertNum("7.00", r.lines().get(indexOf(r, "BUCH FREUNDE GEF")).taxRatePercent());
    }

    // ---- privacy -----------------------------------------------------------------------------

    @Test
    void noCardTerminalTraceOrSignatureDataInTheResult() {
        ParsedReceipt r = penny(fixture("penny_made_up_full.txt"));
        String all = r.toString();
        for (String secret : List.of("5678", "0001", "61626364", "090807", "07 315 19", "Terminal", "Trace",
                "Pos-Info", "AS-Zeit", "Nr.", "Seriennnummer", "MADEUP00SERIAL", "MEUCIQ", "AbCd1234XyZ",
                "Signatur", "TSE", "girocard", "Kartenzahlung", "Contactless")) {
            assertFalse(all.contains(secret), "result leaked " + secret + ":\n" + all);
        }
    }

    @Test
    void cardLinesAreDroppedBeforeParsing() {
        List<String> kept = ReceiptText.lines("""
                Nr.                  ############5678 0001
                Terminal-ID                       61626364
                Trace-Nr.                           090807
                Pos-Info                         07 315 19
                AS-Zeit 03.10.                   17:42 Uhr
                Seriennnummer Kasse: MADEUP00SERIAL
                AbCd1234XyZ=
                Beleg-Nr.                             4711
                """);
        assertEquals(List.of("Beleg-Nr.                             4711"), kept);
    }
}
