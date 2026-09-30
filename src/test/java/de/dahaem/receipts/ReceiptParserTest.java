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
 * Parses text fixtures laid out like the ALDI and PENNY receipts the layouts were derived from.
 * Item names and card/terminal values in the fixtures are made up; the layouts, prices in the
 * task-relevant lines, and totals follow the real receipts.
 */
class ReceiptParserTest {

    private final ReceiptParser parser = new ReceiptParser();

    private static String fixture(String name) {
        try (InputStream in = ReceiptParserTest.class.getResourceAsStream("/receipts/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ParsedReceipt parse(String name) {
        String text = fixture(name);
        return parser.parse(text, parser.detectFormat(text));
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

    // ---- format detection -------------------------------------------------------------------

    @Test
    void detectsStoreFromHeaderMarkers() {
        assertEquals(StoreFormat.ALDI, parser.detectFormat(fixture("aldi_2026-09-25.txt")));
        assertEquals(StoreFormat.PENNY, parser.detectFormat(fixture("penny_2026-09-26.txt")));
        assertEquals(StoreFormat.PENNY, parser.detectFormat("PENNY\nirgendwas"));
        assertEquals(StoreFormat.UNKNOWN, parser.detectFormat("Bäckerei Müller\nBrötchen 0,50"));
    }

    // ---- ALDI 25.09.26 ----------------------------------------------------------------------

    @Test
    void aldiSimpleReceipt() {
        ParsedReceipt r = parse("aldi_2026-09-25.txt");

        assertEquals("ALDI", r.storeBrand());
        assertEquals("Waldweg 1-5, 27356 Roterburg", r.storeAddress());
        assertEquals(LocalDateTime.of(2026, 9, 25, 8, 46), r.purchasedAt());
        assertEquals("EUR", r.currency());
        assertNum("14.83", r.total());
        assertNull(r.totalSavings());
        assertEquals(7, r.lines().size());
        assertTrue(r.adjustments().isEmpty());
        assertTrue(r.balances());
        assertNum("0", r.delta());

        // footer table drives the mapping: ALDI 1 -> 7 %, 2 -> 19 %
        assertEquals(2, r.taxBreakdown().size());
        ReceiptLine chrysanthemen = r.lines().get(indexOf(r, "CHRYSANTHEME"));
        assertEquals("1", chrysanthemen.taxSymbol());
        assertNum("7.00", chrysanthemen.taxRatePercent());
        ReceiptLine seife = r.lines().get(indexOf(r, "FLÜSSIGSEIFE"));
        assertNum("19.00", seife.taxRatePercent());
        assertNum("1.99", seife.lineTotal());
        assertNum("1", seife.quantity());
    }

    @Test
    void aldiMultiBuyLine() {
        ParsedReceipt r = parse("aldi_2026-09-25.txt");
        ReceiptLine l = r.lines().get(indexOf(r, "CHRYSANTHEME"));
        assertNum("3", l.quantity());
        assertNum("1.99", l.unitPrice());
        assertNum("5.97", l.lineTotal());
        assertNum("1.99", r.paidUnitPrice(indexOf(r, "CHRYSANTHEME")));
    }

    @Test
    void privacyDataFromPaymentFooterIsNotRetained() {
        for (String name : List.of("aldi_2026-09-25.txt", "aldi_2026-09-26.txt", "penny_2026-09-26.txt")) {
            ParsedReceipt r = parse(name);
            String unparsed = String.join("\n", r.unparsedLines());
            for (String secret : List.of("9999", "1234", "12345678", "87654321", "654321", "112233",
                    "A0000003591010", "MEUC", "MEQC", "Terminal", "TA-Nr", "TSE", "Karte:", "Karten-Nr")) {
                assertFalse(unparsed.contains(secret), name + " leaked " + secret + " in:\n" + unparsed);
            }
            assertFalse(r.toString().contains("************"), name);
        }
    }

    @Test
    void unrecognisedCommercialLinesAreKeptNotDropped() {
        ParsedReceipt r = parse("aldi_2026-09-25.txt");
        assertEquals(List.of("Girocard                  14,83 €"), r.unparsedLines());
    }

    // ---- ALDI 26.09.26 ----------------------------------------------------------------------

    @Test
    void aldiWeightLineAndPositionsrabatt() {
        ParsedReceipt r = parse("aldi_2026-09-26.txt");
        int i = indexOf(r, "SCHWEINEFILET");
        ReceiptLine l = r.lines().get(i);

        assertNum("0.865", l.weightKg());
        assertNum("8.49", l.pricePerKg());
        assertEquals("kg", l.unit());
        assertNum("0.865", l.quantity());
        assertNum("7.34", l.lineTotal());

        List<ReceiptAdjustment> adj = r.adjustmentsFor(i);
        assertEquals(1, adj.size());
        assertEquals(AdjustmentType.POSITIONSRABATT, adj.get(0).type());
        assertEquals("Positionsrabatt 30%", adj.get(0).description());
        assertNum("-2.20", adj.get(0).amount());
        assertNum("5.14", r.paidTotal(i));
        assertNum("7.34", l.lineTotal()); // listed price stays separate from the paid price
    }

    @Test
    void aldiMultiBuysTotalsAndBalance() {
        ParsedReceipt r = parse("aldi_2026-09-26.txt");
        ReceiptLine kaese = r.lines().get(indexOf(r, "KÄSEAUFSCHNITT"));
        assertNum("2", kaese.quantity());
        assertNum("3.99", kaese.unitPrice());
        ReceiptLine oel = r.lines().get(indexOf(r, "OLIVENÖL"));
        assertNum("2", oel.quantity());
        assertNum("12.99", oel.unitPrice());
        assertNum("19.00", oel.taxRatePercent());

        assertEquals(8, r.lines().size());
        assertNum("68.46", r.total());
        assertNum("2.20", r.totalSavings());
        assertEquals(LocalDateTime.of(2026, 9, 26, 17, 2), r.purchasedAt());
        assertTrue(r.balances());
        assertNum("0", r.delta());
        assertNum("68.46", r.computedTotal());
    }

    // ---- PENNY 26.09.26 ---------------------------------------------------------------------

    @Test
    void pennyHeaderAndTaxMappingFromFooter() {
        ParsedReceipt r = parse("penny_2026-09-26.txt");

        assertEquals("PENNY", r.storeBrand());
        assertEquals("Bremer Straße 16, 27383 Scheeßel", r.storeAddress());
        assertEquals("1005", r.storeId());
        assertEquals(LocalDateTime.of(2026, 9, 26, 13, 17), r.purchasedAt());
        assertNum("75.01", r.total());
        assertEquals(2, r.taxBreakdown().size());

        // Opposite of ALDI: here A = 19 %, B = 7 %
        ReceiptLine steak = r.lines().get(indexOf(r, "RINDERSTEAK"));
        assertEquals("B", steak.taxSymbol());
        assertNum("7.00", steak.taxRatePercent());
        ReceiptLine wasch = r.lines().get(indexOf(r, "WASCHMITTEL"));
        assertEquals("A", wasch.taxSymbol());
        assertNum("19.00", wasch.taxRatePercent());

        TaxBreakdownEntry a = r.taxBreakdown().get(0);
        assertEquals("A", a.symbol());
        assertNum("33.07", a.net());
        assertNum("6.28", a.tax());
        assertNum("39.35", a.gross());
    }

    @Test
    void pennyWeightAndMultiBuy() {
        ParsedReceipt r = parse("penny_2026-09-26.txt");
        ReceiptLine bananen = r.lines().get(indexOf(r, "BANANEN"));
        assertNum("0.772", bananen.weightKg());
        assertNum("2.89", bananen.pricePerKg());
        assertNum("2.23", bananen.lineTotal());

        ReceiptLine joghurt = r.lines().get(indexOf(r, "JOGHURT"));
        assertNum("2", joghurt.quantity());
        assertNum("2.99", joghurt.unitPrice());
        assertNum("5.98", joghurt.lineTotal());
    }

    @Test
    void pennyAppDiscountIsSeparateFromTheProduct() {
        ParsedReceipt r = parse("penny_2026-09-26.txt");
        int i = indexOf(r, "HACKFLEISCH");
        assertNum("4.99", r.lines().get(i).lineTotal());
        ReceiptAdjustment app = r.adjustmentsFor(i).get(0);
        assertEquals(AdjustmentType.APP_RABATT, app.type());
        assertNum("-0.40", app.amount());
        assertNum("4.59", r.paidTotal(i));
    }

    @Test
    void pennyLoyaltyStickerPairsWithPrecedingArticle() {
        ParsedReceipt r = parse("penny_2026-09-26.txt");
        int i = indexOf(r, "DUSCHTUCH BLAU");
        assertNum("24.99", r.lines().get(i).lineTotal());
        ReceiptAdjustment t = r.adjustmentsFor(i).get(0);
        assertEquals(AdjustmentType.LOYALTY_STICKER, t.type());
        assertNum("-22.00", t.amount());
        assertNum("2.99", r.paidTotal(i));
        assertEquals(7, r.loyaltyPointsEarned());
    }

    @Test
    void pennyDepositAndReturnAreNotDiscountsOrProducts() {
        ParsedReceipt r = parse("penny_2026-09-26.txt");
        assertEquals(13, r.lines().size()); // Pfand and Leergut are not product lines
        assertTrue(r.lines().stream().noneMatch(l -> l.rawName().startsWith("PFAND") || l.rawName().startsWith("LEERG")));

        int water = indexOf(r, "MINERALWASSER");
        ReceiptAdjustment pfand = r.adjustmentsFor(water).get(0);
        assertEquals(AdjustmentType.PFAND_DEPOSIT, pfand.type());
        assertNum("0.25", pfand.amount());
        assertNum("0.25", r.lines().get(water).deposit());
        assertNum("0.49", r.paidTotal(water)); // deposit does not change the product's paid price

        ReceiptAdjustment leergut = r.adjustments().stream()
                .filter(a -> a.type() == AdjustmentType.LEERGUT_RETURN).findFirst().orElseThrow();
        assertNum("-0.50", leergut.amount());
        assertNull(leergut.lineIndex());
        assertFalse(leergut.type().isDiscount());
        assertFalse(AdjustmentType.PFAND_DEPOSIT.isDiscount());
    }

    @Test
    void pennyReconcilesWithPrintedTotal() {
        ParsedReceipt r = parse("penny_2026-09-26.txt");
        assertTrue(r.balances());
        assertNum("0", r.delta());
        assertNum("75.01", r.computedTotal());
        assertEquals(4, r.adjustments().size());
    }

    // ---- reconciliation, tolerance, parsing details ------------------------------------------

    @Test
    void mismatchIsReportedNotThrown() {
        String misOcr = fixture("aldi_2026-09-25.txt").replace("ZAHNPASTA                  1,51", "ZAHNPASTA                  1,81");
        ParsedReceipt r = parser.parse(misOcr, StoreFormat.ALDI);
        assertFalse(r.balances());
        assertNum("0.30", r.delta());
    }

    @Test
    void missingTotalDoesNotBalance() {
        ParsedReceipt r = parser.parse("A L D I\nBUTTER 1,29 € 1", StoreFormat.ALDI);
        assertNull(r.total());
        assertNull(r.delta());
        assertFalse(r.balances());
        assertNull(r.lines().get(0).taxRatePercent()); // no footer table -> no guessed rate
    }

    @Test
    void germanDecimalCommaIsParsed() {
        assertEquals(0, new BigDecimal("1234.56").compareTo(ReceiptText.money("1.234,56")));
        assertEquals(0, new BigDecimal("-2.20").compareTo(ReceiptText.money("-2,20")));
        assertEquals(0, new BigDecimal("-2.20").compareTo(ReceiptText.money("−2,20")));
        assertEquals(new BigDecimal("0.865"), ReceiptText.kilos("0,865"));
    }

    @Test
    void unknownFormatKeepsSanitisedLinesForReview() {
        ParsedReceipt r = parser.parse("Bäckerei Müller\nBrötchen 0,50\nTerminal-ID: 99999999", StoreFormat.UNKNOWN);
        assertEquals(List.of("Bäckerei Müller", "Brötchen 0,50"), r.unparsedLines());
        assertTrue(r.lines().isEmpty());
        assertFalse(r.balances());
    }

    @Test
    void parseFromTextSourceDetectsFormat() {
        String text = fixture("aldi_2026-09-26.txt");
        ParsedReceipt r = parser.parse((ReceiptTextSource) () -> text);
        assertEquals("ALDI", r.storeBrand());
        assertTrue(r.balances());
    }
}
