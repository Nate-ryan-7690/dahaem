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

    /** Descriptions of the adjustments attached to no line, in printed order. */
    private static List<String> unattached(ParsedReceipt r) {
        return r.adjustments().stream().filter(a -> a.lineIndex() == null).map(ReceiptAdjustment::description).toList();
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

    // ---- credit-card payment block, newer footer wording -------------------------------------

    @Test
    void visaReceiptBalancesAndLeavesNothingUnparsed() {
        ParsedReceipt r = penny(fixture("penny_made_up_visa.txt"));
        assertEquals(List.of(), r.unparsedLines());
        assertEquals(4, r.lines().size());
        assertNum("37.96", r.total());
        assertTrue(r.balances());
        assertEquals(LocalDateTime.of(2026, 10, 4, 9, 15), r.purchasedAt());
        assertEquals("0815", r.receiptNumber());
        assertEquals(2, r.taxBreakdown().size());
    }

    @Test
    void visaPaymentBlockLeavesNoBankOrCardDataInTheResult() {
        ParsedReceipt r = penny(fixture("penny_made_up_visa.txt"));
        String all = r.toString() + "\n" + String.join("\n", r.unparsedLines());
        for (String secret : List.of("VISA", "MUSTERBANK", "BEISPIELORT", "Bezahlung", "APPROVED", "Proc-Code",
                "13 579 24", "Capt", "9753", "4321", "0002", "71727374", "246810", "05 123 07", "AS-Zeit",
                "Pos-Info", "Terminal", "Trace", "Contactless", "37,96", "MADEUP01SERIAL", "MEUCIQ",
                "XyZaBcDeFgHiJkLmNoPq")) {
            assertFalse(all.contains(secret), "result leaked " + secret + ":\n" + all);
        }
    }

    @Test
    void anyLineInsideThePaymentBlockIsSkippedNeverUnparsed() {
        ParsedReceipt r = penny("""
                     BROT                            1,89 B
                     SUMME                    EUR     1,89
                     Geg. Mastercard          EUR     1,89
                     Bezahlung
                     MASTERCARD IRGENDEINE BANK
                     Some Unknown Payment Line 42
                                 Betrag EUR
                                                      1,89
                     Steuer %        Netto    Steuer   Brutto
                     B=  7,0%         1,77      0,12     1,89
                     Gesamtbetrag     1,77      0,12     1,89
                     Unbekannter Text
                """);
        assertEquals(List.of("Unbekannter Text"), r.unparsedLines()); // outside the block: still reported
        assertEquals(1, r.taxBreakdown().size());
        assertTrue(r.balances());
        assertFalse(r.toString().contains("BANK"));
    }

    @Test
    void appSavingsOnTwoLinesAndOnOneLine() {
        assertNum("1.00", penny(fixture("penny_made_up_visa.txt")).appSavings());
        ParsedReceipt oneLine = penny("SUMME  EUR  1,89\nMit der PENNY App 0,20 EUR gespart");
        assertNum("0.20", oneLine.appSavings());
        assertTrue(oneLine.unparsedLines().isEmpty(), oneLine.unparsedLines().toString());
    }

    @Test
    void newerFooterWordingIsRecognised() {
        ParsedReceipt r = penny(fixture("penny_made_up_visa.txt"));
        assertEquals(20, r.loyaltyPointsEarned());
        assertNum("22.00", r.otherSavings());
        assertNum("23.00", r.totalSavings());
    }

    @Test
    void benefitsSumWithoutSonstigeVorteileLineIsOtherSavings() {
        ParsedReceipt r = penny("""
                     DUSCHTUCH BLAU                  24,99 A *
                     Treueartikel                   -22,00
                     SUMME                    EUR     2,99
                     Deine zusätzlichen Vorteile heute:
                     Summe                           22,00
                """);
        assertNum("22.00", r.otherSavings());
        assertTrue(r.unparsedLines().isEmpty(), r.unparsedLines().toString());
        assertTrue(r.balances());
    }

    @Test
    void strayBase64LineOnItsOwnIsDropped() {
        assertEquals(List.of(), ReceiptText.lines("XyZaBcDeFgHiJkLmNoPq\nAbCdEfGhIjKlMnOp\nQUJDREVGR0g=\n"));
        assertEquals(List.of("Contactless", "KINDERPONCHO"), ReceiptText.lines("Contactless\nKINDERPONCHO"));
    }

    // ---- app coupons: plain and brand coupons belong to the product before them, group coupons to none

    @Test
    void plainAndBrandCouponsAttachToThePrecedingProductGroupCouponsToNone() {
        ParsedReceipt r = penny("""
                     HACKFLEISCH                     4,49 B
                     App-Coupon                     -1,00 B
                     KONFITUERE                      2,49 B
                     App-Coupon 10% Waren           -0,25 B
                     CHIPS GESALZEN                  1,79 B
                     App-Coupon 7% Chips            -0,13 B
                     KINDER RIEGEL                   2,29 B
                     Ferrero Kinder 25%             -0,57 B
                     SUMME                    EUR     9,11
                """);
        assertEquals(List.of("HACKFLEISCH", "KONFITUERE", "CHIPS GESALZEN", "KINDER RIEGEL"),
                r.lines().stream().map(ReceiptLine::rawName).toList(), "coupons are not products");
        assertTrue(r.unparsedLines().isEmpty(), r.unparsedLines().toString());
        assertEquals(4, r.adjustments().size());
        assertTrue(r.adjustments().stream().allMatch(a -> a.type() == AdjustmentType.APP_COUPON && a.type().isDiscount()));

        ReceiptAdjustment kinder = r.adjustmentsFor(indexOf(r, "KINDER RIEGEL")).get(0);
        assertEquals("Ferrero Kinder 25%", kinder.description());
        assertNum("-0.57", kinder.amount());
        assertNum("3.49", r.paidTotal(indexOf(r, "HACKFLEISCH")));
        assertEquals(List.of(), r.adjustmentsFor(indexOf(r, "KONFITUERE")), "a group coupon is not the product's");
        assertNum("2.49", r.paidTotal(indexOf(r, "KONFITUERE")));
        assertNum("1.79", r.paidTotal(indexOf(r, "CHIPS GESALZEN")));
        assertEquals(List.of("App-Coupon 10% Waren", "App-Coupon 7% Chips"), unattached(r));
        assertTrue(r.balances());
    }

    @Test
    void groupCouponBlockAfterTheLastProductBelongsToNoLine() {
        ParsedReceipt r = penny("""
                     TK GEMUESEPFANNE                2,98 B
                     ORANGENSAFT                     1,99 A
                     CHIPS PAPRIKA                   1,79 B
                     App-Coupon 10% Waren           -0,30 B
                     App-Coupon 5% Säfte            -0,10 A
                     App-Coupon 10% Bäcke           -0,18 B
                     SUMME                    EUR     6,18
                """);
        assertEquals(3, r.lines().size());
        assertEquals(List.of("App-Coupon 10% Waren", "App-Coupon 5% Säfte", "App-Coupon 10% Bäcke"), unattached(r));
        for (int i = 0; i < r.lines().size(); i++) {
            assertEquals(r.lines().get(i).lineTotal(), r.paidTotal(i), "shelf price kept: " + r.lines().get(i).rawName());
        }
        assertNum("-0.10", r.adjustments().get(1).amount());
        assertTrue(r.balances());
    }

    @Test
    void groupCouponAfterAnUnrelatedProductBelongsToNoLine() {
        ParsedReceipt r = penny("""
                     SPEZI                           0,95 A
                     App-Coupon 7% Chips            -0,13 B
                     App-Coupon                     -0,20 A
                     BROT                            1,49 B
                     App-Coupon 10%                 -0,15 B
                     SUMME                    EUR     1,96
                """);
        assertNum("0.75", r.paidTotal(indexOf(r, "SPEZI"))); // only the plain coupon is the Spezi's
        assertNum("1.49", r.paidTotal(indexOf(r, "BROT")));
        assertEquals(List.of("App-Coupon 7% Chips", "App-Coupon 10%"), unattached(r), "a group word is optional");
        assertTrue(r.balances());
    }

    @Test
    void groupCouponWithATrailingCommaIsStillAGroupCoupon() {
        ParsedReceipt r = penny("""
                     CHIPS PAPRIKA                   1,79 B
                     App-Coupon 5% Chips,           -0,09 B
                     SUMME                    EUR     1,70
                """);
        assertEquals(List.of("App-Coupon 5% Chips,"), unattached(r), "raw text kept as printed");
        assertNum("1.79", r.paidTotal(0));
        assertTrue(r.unparsedLines().isEmpty(), r.unparsedLines().toString());
        assertTrue(r.balances());
    }

    @Test
    void aFullPlainCouponMakesTheItemFree() {
        ParsedReceipt r = penny("""
                     BROT                            1,49 B
                     App-Coupon                     -1,49 B
                     SUMME                    EUR     0,00
                """);
        assertNum("0.00", r.paidTotal(0));
        assertEquals(1, r.adjustmentsFor(0).size());
        assertTrue(r.balances());
    }

    @Test
    void couponAfterAppPriceDiscountStillAttachesToTheSameProduct() {
        ParsedReceipt r = penny("""
                     ESL MILCH 3,5%                  1,19 B
                     App-Preis-Rabatt               -0,20 B
                     App-Coupon                     -0,10 B
                     SUMME                    EUR     0,89
                """);
        assertEquals(1, r.lines().size());
        assertEquals(2, r.adjustmentsFor(0).size());
        assertNum("0.89", r.paidTotal(0));
        assertTrue(r.balances());
    }

    @Test
    void productNamesWithAPercentageStayProducts() {
        ParsedReceipt r = penny("""
                     ESL MILCH 3,5%                  1,19 B
                     JOGHURT 10%                     0,79 B
                     SUMME                    EUR     1,98
                """);
        assertEquals(2, r.lines().size());
        assertTrue(r.adjustments().isEmpty());
        assertTrue(r.balances());
    }

    @Test
    void freshnessDiscountHasItsOwnType() {
        ParsedReceipt r = penny("""
                     FRUCHTZWERGE                    1,79 B
                     Frischerabatt                  -0,90 B
                     SUMME                    EUR     0,89
                """);
        ReceiptAdjustment a = r.adjustmentsFor(0).get(0);
        assertEquals(AdjustmentType.FRESHNESS_DISCOUNT, a.type());
        assertTrue(a.type().isDiscount());
        assertTrue(r.balances());
    }

    @Test
    void aPercentageRabattLineStaysAGenericDiscount() {
        ParsedReceipt r = penny("""
                     BROT                            1,89 B
                     Rabatt 20%                     -0,38 B
                     SUMME                    EUR     1,51
                """);
        assertEquals(AdjustmentType.OTHER, r.adjustmentsFor(0).get(0).type());
        assertTrue(r.balances());
    }

    @Test
    void boxDrawingApostropheIsReadAsAnApostrophe() {
        ParsedReceipt r = penny("""
                     Ben & Jerry┤s                   4,99 B
                     SUMME                    EUR     4,99
                """);
        assertEquals("Ben & Jerry's", r.lines().get(0).rawName());
        assertTrue(r.balances());
    }
}
