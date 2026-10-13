package de.dahaem.receipts;

import de.dahaem.shared.Database;
import de.dahaem.stores.StoreRepository;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The receipt import end to end on a throwaway MariaDB (Testcontainers), never the live database:
 * schema.sql plus the real seed catalog and the real config/receipt_overrides.json, and made-up
 * receipts in the PENNY layout (no real receipt is used).
 */
class ReceiptImportTest {

    private static final Path MIGRATION = Path.of("migrations", "0008_purchase_receipts.sql");
    private static final Path CATALOG_MIGRATION = Path.of("migrations", "0009_catalog_receipt_items.sql");
    private static final ReceiptMatcher.Overrides OVERRIDES =
            ReceiptMatcher.Overrides.load(Path.of("config", "receipt_overrides.json"));

    @BeforeEach
    void setUp() {
        TestDatabase.reset(List.of(Path.of("schema.sql"), Path.of("seed", "inventory_catalog.sql")));
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    @Test
    void dryRunWritesNothing() throws SQLException {
        ReceiptImportReport report = service().run(receipts(), false);

        assertFalse(report.committed());
        assertEquals(2, report.count(ReceiptImportService.Status.NEW));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt_line"));
        assertEquals(0, count("SELECT COUNT(*) FROM store"), "a dry run does not even create the store row");
        assertTrue(report.toText().contains("DRY RUN"));
    }

    @Test
    void commitStoresHeaderEveryLineAndEveryAdjustment() throws SQLException {
        ReceiptImportReport report = service().run(receipts(), true);

        assertTrue(report.committed());
        assertEquals(2, report.count(ReceiptImportService.Status.NEW));
        assertEquals(2, count("SELECT COUNT(*) FROM purchase_receipt"));
        assertEquals(19, count("SELECT COUNT(*) FROM purchase_receipt_line"), "16 + 3 product lines, coupons are not lines");
        assertEquals(9, count("SELECT COUNT(*) FROM purchase_receipt_adjustment"));
        assertEquals(2, count("SELECT COUNT(*) FROM purchase_receipt WHERE purchase_receipt_receipt_number = '0815'"),
                "the same 4-digit number on another day is another receipt");

        Map<String, Object> head = row("SELECT purchase_receipt_total, purchase_receipt_app_savings, "
                + "purchase_receipt_other_savings, purchase_receipt_loyalty_points, purchase_receipt_purchased_at "
                + "FROM purchase_receipt ORDER BY purchase_receipt_purchased_at LIMIT 1");
        assertNum("34.33", head.get("purchase_receipt_total"));
        assertNum("2.45", head.get("purchase_receipt_app_savings"));
        assertNum("0.90", head.get("purchase_receipt_other_savings"));
        assertEquals(2, ((Number) head.get("purchase_receipt_loyalty_points")).intValue());
        assertEquals("2026-10-05 18:03:00.0", head.get("purchase_receipt_purchased_at").toString());

        Map<String, Object> bananen = line("BANANEN");
        assertNum("0.820", bananen.get("purchase_receipt_line_quantity"));
        assertEquals("kg", bananen.get("purchase_receipt_line_unit"));
        assertNum("1.50", bananen.get("purchase_receipt_line_price_per_kg"));
        Map<String, Object> milch = line("ESL MILCH 1,5%");
        assertNum("2", milch.get("purchase_receipt_line_quantity"));
        assertNum("1.19", milch.get("purchase_receipt_line_unit_price"));
        assertNum("7.00", milch.get("purchase_receipt_line_tax_rate"));
        assertNum("0.25", line("VIT.Z. RASPBERRY").get("purchase_receipt_line_deposit"));
    }

    @Test
    void secondRunFindsOnlyDuplicatesAndWritesNoRow() throws SQLException {
        service().run(receipts(), true);

        ReceiptImportReport again = service().run(receipts(), true);

        assertEquals(0, again.count(ReceiptImportService.Status.NEW));
        assertEquals(2, again.count(ReceiptImportService.Status.DUPLICATE));
        assertEquals(2, count("SELECT COUNT(*) FROM purchase_receipt"));
        assertEquals(19, count("SELECT COUNT(*) FROM purchase_receipt_line"));
        assertEquals(9, count("SELECT COUNT(*) FROM purchase_receipt_adjustment"));
        assertEquals(1, count("SELECT COUNT(*) FROM store"));
    }

    @Test
    void theSameFileTwiceInOneRunIsADuplicate() throws SQLException {
        List<ReceiptImportService.ReceiptFile> files = new ArrayList<>(receipts());
        files.add(new ReceiptImportService.ReceiptFile("copy.txt", fixture("penny_made_up_import_a.txt")));

        ReceiptImportReport report = service().run(files, true);

        assertEquals(1, report.count(ReceiptImportService.Status.DUPLICATE));
        assertEquals(2, count("SELECT COUNT(*) FROM purchase_receipt"));
    }

    @Test
    void storedLinesAndAdjustmentsAddUpToThePrintedTotal() throws SQLException {
        ReceiptImportReport report = service().run(receipts(), true);

        // paid totals already contain the discounts attached to their line; everything else is added once
        List<String> checks = strings("SELECT CONCAT(r.purchase_receipt_total, '=', "
                + "(SELECT SUM(l.purchase_receipt_line_paid_total) FROM purchase_receipt_line l "
                + "  WHERE l.purchase_receipt_line_receipt_id = r.purchase_receipt_id) + "
                + "(SELECT COALESCE(SUM(a.purchase_receipt_adjustment_amount), 0) FROM purchase_receipt_adjustment a "
                + "  WHERE a.purchase_receipt_adjustment_receipt_id = r.purchase_receipt_id "
                + "  AND NOT (a.purchase_receipt_adjustment_line_id IS NOT NULL "
                + "       AND a.purchase_receipt_adjustment_type NOT IN ('DEPOSIT', 'DEPOSIT_RETURN')))) "
                + "FROM purchase_receipt r ORDER BY r.purchase_receipt_purchased_at");
        assertEquals(List.of("34.33=34.33", "7.27=7.27"), checks);
        assertEquals(checks, balanceChecks());
        assertNum("41.60", report.newTotal());
        assertTrue(report.allBalanced());
        assertTrue(report.toText().contains("printed 34.33  OK"), report.toText());
    }

    @Test
    void couponsAndDiscountsAreAttachedToTheProductBefore() throws SQLException {
        service().run(receipts(), true);

        assertEquals(List.of("App-Coupon APP_COUPON -1.00"), adjustmentsOf("Hä.-Brustfilet"));
        assertEquals(List.of("Ferrero Kinder 25% APP_COUPON -0.57"), adjustmentsOf("Kinder Riegel"));
        assertEquals(List.of(), adjustmentsOf("LAYS GESALZEN"), "a group coupon is not the product's");
        assertEquals(List.of(), adjustmentsOf("Landliebe Konfit"));
        assertEquals(List.of("App-Preis-Rabatt APP_PRICE_DISCOUNT -0.50"), adjustmentsOf("Hackfleisch gem."));
        assertEquals(List.of("Frischerabatt FRESHNESS_DISCOUNT -0.90"), adjustmentsOf("FRUCHTZWERGE"));
        assertEquals(List.of("PFAND 0,25 EURO DEPOSIT 0.25"), adjustmentsOf("Krom FB Kirsche"));
        assertNum("1.79", line("LAYS GESALZEN").get("purchase_receipt_line_paid_total"));
        assertNum("1.79", line("LAYS GESALZEN").get("purchase_receipt_line_line_total"));
        assertNum("0.99", line("Krom FB Kirsche").get("purchase_receipt_line_paid_total")); // a deposit is not part of the price
        assertEquals(1, count("SELECT COUNT(*) FROM purchase_receipt_adjustment WHERE purchase_receipt_adjustment_line_id IS NULL "
                + "AND purchase_receipt_adjustment_type = 'DEPOSIT_RETURN'"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt_line WHERE purchase_receipt_line_raw_name LIKE '%Coupon%' "
                + "OR purchase_receipt_line_raw_name LIKE '%25\\%%'"));
    }

    @Test
    void groupCouponsAreStoredWithoutALineAndProductsKeepTheirShelfPrice() throws SQLException {
        ReceiptImportReport report = service().run(List.of(file("penny_made_up_import_c.txt")), true);

        assertEquals(1, report.count(ReceiptImportService.Status.NEW));
        // printed order; the plain "App-Coupon -1.00" is the safety-net case (see the next test)
        assertEquals(List.of("App-Coupon 10% Waren -0.58", "App-Coupon 7% Chips -0.13", "App-Coupon -1.00",
                "App-Coupon 5% Chips, -0.09", "App-Coupon 5% Säfte -0.10", "App-Coupon 10% Bäcke -0.15"),
                strings("SELECT CONCAT(purchase_receipt_adjustment_raw_text, ' ', purchase_receipt_adjustment_amount) "
                        + "FROM purchase_receipt_adjustment WHERE purchase_receipt_adjustment_line_id IS NULL "
                        + "AND purchase_receipt_adjustment_type = 'APP_COUPON' ORDER BY purchase_receipt_adjustment_id"));
        for (String name : List.of("TK GEMUESEPFANNE", "SPEZI", "CHIPS PAPRIKA", "ORANGENSAFT")) {
            assertEquals(List.of(), adjustmentsOf(name), name);
            assertEquals(0, new BigDecimal(line(name).get("purchase_receipt_line_line_total").toString())
                    .compareTo(new BigDecimal(line(name).get("purchase_receipt_line_paid_total").toString())), name);
        }
        // plain and brand coupons stay on their product; a 100 % plain coupon is a free item
        assertEquals(List.of("Ferrero Kinder 25% APP_COUPON -0.57"), adjustmentsOf("KINDER BUENO"));
        assertNum("1.72", line("KINDER BUENO").get("purchase_receipt_line_paid_total"));
        assertEquals(List.of("App-Coupon APP_COUPON -1.49"), adjustmentsOf("BROT"));
        assertNum("0.00", line("BROT").get("purchase_receipt_line_paid_total"));
        assertEquals(List.of("7.88=7.88"), balanceChecks());
        assertEquals(5, report.groupCoupons());
        assertTrue(report.toText().contains("discounts without a line: 5 group coupons, 1 by the negative-paid safety net; "
                + "lines with a negative paid total: 0"), report.toText());
    }

    @Test
    void aDiscountThatWouldMakeThePaidTotalNegativeIsStoredWithoutALineAndReported() throws SQLException {
        ReceiptImportReport report = service().run(List.of(file("penny_made_up_import_c.txt")), true);

        assertEquals(List.of(), adjustmentsOf("KAUGUMMI"));
        assertNum("0.50", line("KAUGUMMI").get("purchase_receipt_line_paid_total"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt_line WHERE purchase_receipt_line_paid_total < 0"));
        ReceiptImportService.ReceiptResult r = report.receipts().get(0);
        assertEquals(List.of("App-Coupon -1.00 on line 5 KAUGUMMI 0.50"), r.unattached());
        assertEquals(1, report.safetyNetUnattached());
        assertEquals(0, report.negativePaidLines());
        assertTrue(report.toText().contains("stored without a line (paid would be negative): App-Coupon -1.00 on line 5 KAUGUMMI"),
                report.toText());
        assertTrue(report.allBalanced());
    }

    @Test
    void everyMadeUpReceiptStillBalances() throws SQLException {
        List<ReceiptImportService.ReceiptFile> all = new ArrayList<>(receipts());
        all.add(file("penny_made_up_import_c.txt"));
        all.add(file("penny_made_up_import_d.txt"));

        ReceiptImportReport report = service().run(all, true);

        assertEquals(4, report.count(ReceiptImportService.Status.NEW));
        assertTrue(report.allBalanced(), report.toText());
        assertEquals(0, report.negativePaidLines());
        assertEquals(List.of("34.33=34.33", "7.88=7.88", "19.77=19.77", "7.27=7.27"), balanceChecks());
    }

    @Test
    void nateDecisionsOfOctober13MapTheNewAndReusedItems() throws SQLException {
        service().run(List.of(file("penny_made_up_import_d.txt")), true);

        assertItem("Rosinen Brötchen", 575, "override");      // existing rosinenbrötchen, not 569 brötchen
        assertItem("Gourmetbrötchen", 1818, "override");
        assertItem("Mandarin Orangen", 892, "override");      // existing mandarinen dose, not 181 orange
        assertItem("RINDERSALAMI", 1819, "override");
        assertItem("EDELSALAMI", 302, "rule");                // stays salami
        assertItem("GFL-FLEISCHWURST", 1820, "override");
        assertItem("Minigurken", 59, "override");             // existing snackgurke, not 58 gurke
        assertItem("SOUR CREAM", 1821, "override");
        assertItem("BACON-STREIFEN", 1536, "override");
        for (String name : List.of("BACON", "DEL. BACON", "BACON XXL", "C & W Brötchen")) {
            assertNull(line(name).get("purchase_receipt_line_inventory_item_id"), name + " waits for Nate's review");
            assertNull(line(name).get("purchase_receipt_line_match_source"), name);
        }
    }

    @Test
    void catalogMigrationOnlyAddsAndIsReRunnable() throws SQLException {
        String seeded = newItems();
        exec("DELETE FROM inventory_item WHERE inventory_item_id BETWEEN 1818 AND 1821"); // scratch DB only: state before 0009
        String before = checksum();

        TestDatabase.apply(List.of(CATALOG_MIGRATION));
        TestDatabase.apply(List.of(CATALOG_MIGRATION));        // re-runnable

        assertEquals(seeded, newItems(), "the migration adds the same rows as the seed");
        assertEquals(4, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id BETWEEN 1818 AND 1821"));
        exec("DELETE FROM inventory_item WHERE inventory_item_id BETWEEN 1818 AND 1821");
        assertEquals(before, checksum(), "no other row changed");
    }

    @Test
    void capsNamesAndAbbreviationsFindTheirOverrides() throws SQLException {
        service().run(receipts(), true);

        assertItem("ESL MILCH 3,5%", 356, "override");
        assertItem("ESL MILCH 1,5%", 357, "override");
        assertItem("Hackfleisch gem.", 271, "override");
        assertItem("Hä.-Brustfilet", 241, "override");
        assertItem("SF Passierte Tom", 824, "override");
        assertItem("Landliebe Konfit", 1689, "override");
        assertItem("LH FRISCHK.NATUR", 381, "override");
        assertItem("LAYS GESALZEN", 1331, "override");
        assertItem("BACON-STREIFEN", 1536, "override");
        assertItem("EIER BH 10ER OKT", 1676, "override");
        assertEquals("Lindenhof", line("LH FRISCHK.NATUR").get("purchase_receipt_line_brand"));
        assertEquals("Landliebe", line("Landliebe Konfit").get("purchase_receipt_line_brand"));
        assertNull(line("Hackfleisch gem.").get("purchase_receipt_line_brand"), "no brand is guessed");
    }

    @Test
    void knownWrongMatchesAndReviewNamesStayUnmatchedButAreStored() throws SQLException {
        ReceiptImportReport report = service().run(receipts(), true);

        for (String name : List.of("VIT.Z. RASPBERRY", "Krom FB Kirsche", "SALATSCHALE ORZO", "FRUCHTZWERGE",
                "MINI GEFL WIENER", "Kinder Riegel", "DUSCHGEL SPORT")) {
            Map<String, Object> l = line(name);
            assertNull(l.get("purchase_receipt_line_inventory_item_id"), name);
            assertNull(l.get("purchase_receipt_line_match_source"), name);
        }
        assertEquals("Ben & Jerry's", line("Ben & Jerry's").get("purchase_receipt_line_raw_name"));
        assertTrue(report.unmatchedNames().containsKey("krom fb kirsche"));
        assertTrue(report.toText().contains("vit z raspberry"), report.toText());
    }

    @Test
    void importNeverTouchesStockOrPurchaseHistory() throws SQLException {
        exec("UPDATE inventory_item SET inventory_item_quantity = 3 WHERE inventory_item_id = 356");
        String withStock = checksum();

        service().run(receipts(), true);

        assertEquals(withStock, checksum(), "inventory_item unchanged");
        assertEquals(3, count("SELECT inventory_item_quantity FROM inventory_item WHERE inventory_item_id = 356"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_history"));
    }

    @Test
    void anErrorRollsBackEveryReceiptOfTheRun() throws SQLException {
        ReceiptRepository failing = new ReceiptRepository() {
            private int receipts;

            @Override
            public int insertReceipt(Connection con, int storeId, ParsedReceipt r) throws SQLException {
                if (++receipts == 2) {
                    throw new SQLException("simulated failure on the second receipt");
                }
                return super.insertReceipt(con, storeId, r);
            }
        };
        ReceiptImportService service = new ReceiptImportService(failing, new StoreRepository()::loadCatalog, OVERRIDES);

        assertThrows(RuntimeException.class, () -> service.run(receipts(), true));

        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt_line"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt_adjustment"));
        assertEquals(0, count("SELECT COUNT(*) FROM store"));
    }

    @Test
    void unbalancedOrIncompleteReceiptsAreSkippedAndReported() throws SQLException {
        String unbalanced = fixture("penny_made_up_import_b.txt").replace("SUMME                    EUR          7,27",
                "SUMME                    EUR          7,30");
        String noNumber = fixture("penny_made_up_import_b.txt").replace("Beleg-Nr.                             0815", "");

        ReceiptImportReport report = service().run(List.of(
                new ReceiptImportService.ReceiptFile("off.txt", unbalanced),
                new ReceiptImportService.ReceiptFile("nonumber.txt", noNumber)), true);

        assertEquals(2, report.count(ReceiptImportService.Status.SKIPPED));
        assertFalse(report.allBalanced());
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt"));
        String text = report.toText();
        assertTrue(text.contains("does not balance"), text);
        assertTrue(text.contains("no receipt number"), text);
    }

    @Test
    void overrideToAMissingItemIsRefusedBeforeAnythingIsWritten() throws SQLException {
        ReceiptMatcher.Overrides broken = new ReceiptMatcher.Overrides(Map.of("bacon", 999999), java.util.Set.of());
        ReceiptImportService service = new ReceiptImportService(new ReceiptRepository(),
                new StoreRepository()::loadCatalog, broken);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.run(receipts(), true));
        assertTrue(e.getMessage().contains("bacon -> 999999"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_receipt"));
    }

    @Test
    void everySeededOverrideIdExistsInTheSeedCatalog() throws SQLException {
        for (Map.Entry<String, Integer> e : OVERRIDES.exact().entrySet()) {
            assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_id = " + e.getValue()), e.getKey());
        }
    }

    @Test
    void migrationIsReRunnableAndMatchesAFreshSchema() throws SQLException {
        List<String> fresh = receiptTableColumns();
        List<String> freshChecks = receiptChecksAndKeys();

        // scratch DB only: schema as it was before 0008
        exec("DROP TABLE purchase_receipt_adjustment");
        exec("DROP TABLE purchase_receipt_line");
        exec("DROP TABLE purchase_receipt");
        TestDatabase.apply(List.of(MIGRATION));
        TestDatabase.apply(List.of(MIGRATION));        // re-runnable

        assertEquals(fresh, receiptTableColumns());
        assertEquals(freshChecks, receiptChecksAndKeys());
        assertEquals(33, fresh.size());
        assertTrue(freshChecks.contains("purchase_receipt.purchase_receipt_store_time_number 0 "
                + "purchase_receipt_store_id,purchase_receipt_purchased_at,purchase_receipt_receipt_number"), freshChecks.toString());
    }

    // ---------- helpers ----------

    private static ReceiptImportService service() {
        return new ReceiptImportService(OVERRIDES);
    }

    private static List<ReceiptImportService.ReceiptFile> receipts() {
        return List.of(
                new ReceiptImportService.ReceiptFile("penny_made_up_import_a.txt", fixture("penny_made_up_import_a.txt")),
                new ReceiptImportService.ReceiptFile("penny_made_up_import_b.txt", fixture("penny_made_up_import_b.txt")));
    }

    private static ReceiptImportService.ReceiptFile file(String name) {
        return new ReceiptImportService.ReceiptFile(name, fixture(name));
    }

    /** Per stored receipt "printed total=lines' paid totals + the adjustments not inside them". */
    private static List<String> balanceChecks() throws SQLException {
        return strings("SELECT CONCAT(r.purchase_receipt_total, '=', "
                + "(SELECT SUM(l.purchase_receipt_line_paid_total) FROM purchase_receipt_line l "
                + "  WHERE l.purchase_receipt_line_receipt_id = r.purchase_receipt_id) + "
                + "(SELECT COALESCE(SUM(a.purchase_receipt_adjustment_amount), 0) FROM purchase_receipt_adjustment a "
                + "  WHERE a.purchase_receipt_adjustment_receipt_id = r.purchase_receipt_id "
                + "  AND NOT (a.purchase_receipt_adjustment_line_id IS NOT NULL "
                + "       AND a.purchase_receipt_adjustment_type NOT IN ('DEPOSIT', 'DEPOSIT_RETURN')))) "
                + "FROM purchase_receipt r ORDER BY r.purchase_receipt_purchased_at");
    }

    private static String newItems() throws SQLException {
        return String.join(";", strings("SELECT CONCAT_WS('|', inventory_item_id, inventory_item_name_english, "
                + "inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, "
                + "COALESCE(inventory_item_subcategory, '-')) FROM inventory_item "
                + "WHERE inventory_item_id BETWEEN 1818 AND 1821 ORDER BY inventory_item_id"));
    }

    private static String fixture(String name) {
        try (InputStream in = ReceiptImportTest.class.getResourceAsStream("/receipts/import/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertItem(String rawName, int itemId, String source) throws SQLException {
        Map<String, Object> l = line(rawName);
        assertEquals(itemId, ((Number) l.get("purchase_receipt_line_inventory_item_id")).intValue(), rawName);
        assertEquals(source, l.get("purchase_receipt_line_match_source"), rawName);
    }

    private static void assertNum(String expected, Object actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())),
                "expected " + expected + " but was " + actual);
    }

    private static String checksum() throws SQLException {
        return strings("SELECT CONCAT(COUNT(*), '/', SUM(inventory_item_quantity), '/', "
                + "MD5(GROUP_CONCAT(CONCAT_WS('|', inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, "
                + "inventory_item_earliest_exp_date) ORDER BY inventory_item_id SEPARATOR ';'))) FROM inventory_item").get(0);
    }

    private static Map<String, Object> line(String rawName) throws SQLException {
        try (Connection con = Database.getConnection();
             var ps = con.prepareStatement("SELECT * FROM purchase_receipt_line WHERE purchase_receipt_line_raw_name = ?")) {
            ps.setString(1, rawName);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "no stored line " + rawName);
                return toMap(rs);
            }
        }
    }

    private static List<String> adjustmentsOf(String rawName) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             var ps = con.prepareStatement("SELECT a.purchase_receipt_adjustment_raw_text, a.purchase_receipt_adjustment_type, "
                     + "a.purchase_receipt_adjustment_amount FROM purchase_receipt_adjustment a JOIN purchase_receipt_line l "
                     + "ON l.purchase_receipt_line_id = a.purchase_receipt_adjustment_line_id "
                     + "WHERE l.purchase_receipt_line_raw_name = ? ORDER BY a.purchase_receipt_adjustment_id")) {
            ps.setString(1, rawName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1) + " " + rs.getString(2) + " " + rs.getBigDecimal(3));
                }
            }
        }
        return out;
    }

    private static Map<String, Object> row(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return toMap(rs);
        }
    }

    private static Map<String, Object> toMap(ResultSet rs) throws SQLException {
        Map<String, Object> out = new HashMap<>();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            out.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
        }
        return out;
    }

    private static List<String> receiptTableColumns() throws SQLException {
        return strings("SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE, ' null=', IS_NULLABLE, "
                + "' default=', COALESCE(COLUMN_DEFAULT, '-')) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'purchase\\_receipt%' "
                + "ORDER BY TABLE_NAME, ORDINAL_POSITION");
    }

    private static List<String> receiptChecksAndKeys() throws SQLException {
        List<String> out = strings("SELECT CONCAT(CONSTRAINT_NAME, ': ', CHECK_CLAUSE) FROM information_schema.CHECK_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'purchase\\_receipt%' ORDER BY CONSTRAINT_NAME");
        out.addAll(strings("SELECT CONCAT(TABLE_NAME, '.', INDEX_NAME, ' ', NON_UNIQUE, ' ', "
                + "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'purchase\\_receipt%' "
                + "GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE ORDER BY TABLE_NAME, INDEX_NAME"));
        return out;
    }

    private static List<String> strings(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(rs.getMetaData().getColumnCount()));
            }
        }
        return out;
    }

    private static int count(String sql) throws SQLException {
        return Integer.parseInt(strings(sql).get(0));
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
