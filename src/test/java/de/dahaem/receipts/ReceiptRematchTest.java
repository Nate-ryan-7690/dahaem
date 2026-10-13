package de.dahaem.receipts;

import de.dahaem.shared.Database;
import de.dahaem.stores.StoreRepository;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ReceiptImportRunner --rematch} on a throwaway MariaDB (Testcontainers), never the live database:
 * the made-up receipts are imported with the real config/receipt_overrides.json, then re-matched with
 * changed overrides.
 */
class ReceiptRematchTest {

    private static final ReceiptMatcher.Overrides OVERRIDES =
            ReceiptMatcher.Overrides.load(Path.of("config", "receipt_overrides.json"));
    private static final int DUSCHGEL = 1810;

    @BeforeEach
    void setUp() {
        TestDatabase.reset(List.of(Path.of("schema.sql"), Path.of("seed", "inventory_catalog.sql")));
        ReceiptImportReport imported = new ReceiptImportService(OVERRIDES).run(List.of(
                file("penny_made_up_import_a.txt"), file("penny_made_up_import_b.txt")), true);
        assertEquals(2, imported.count(ReceiptImportService.Status.NEW));
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    @Test
    void withUnchangedOverridesNothingChanges() throws SQLException {
        ReceiptRematchService.ReceiptRematchReport report = rematch(OVERRIDES, true);

        assertEquals(19, report.linesChecked());
        assertEquals(List.of(), report.changes());
    }

    @Test
    void anUnmatchedLineBecomesMatchedAfterAnOverrideIsAdded() throws SQLException {
        assertEquals("null/null", match("DUSCHGEL SPORT"));

        ReceiptRematchService.ReceiptRematchReport report = rematch(with("duschgel sport", DUSCHGEL), true);

        assertEquals(1, report.changes().size());
        assertEquals(1, report.nowMatched());
        assertEquals(DUSCHGEL + "/override", match("DUSCHGEL SPORT"));
        assertTrue(report.toText().contains("DUSCHGEL SPORT"), report.toText());
        assertTrue(report.toText().contains("unmatched -> 1810 Duschgel (override)"), report.toText());
        assertTrue(report.toText().contains("COMMITTED"), report.toText());
    }

    @Test
    void aManualLineIsNeverChanged() throws SQLException {
        exec("UPDATE purchase_receipt_line SET purchase_receipt_line_inventory_item_id = 270, "
                + "purchase_receipt_line_match_source = 'manual' WHERE purchase_receipt_line_raw_name = 'Hackfleisch gem.'");
        exec("UPDATE purchase_receipt_line SET purchase_receipt_line_inventory_item_id = " + DUSCHGEL + ", "
                + "purchase_receipt_line_match_source = 'manual' WHERE purchase_receipt_line_raw_name = 'BACON-STREIFEN'");
        Set<String> review = new HashSet<>(OVERRIDES.review());
        review.add("bacon streifen");
        Map<String, Integer> exact = new HashMap<>(OVERRIDES.exact());
        exact.remove("bacon streifen");
        exact.put("hackfleisch gemischt", 241);

        ReceiptRematchService.ReceiptRematchReport report = rematch(new ReceiptMatcher.Overrides(exact, review), true);

        assertEquals(17, report.linesChecked(), "manual lines are not even read");
        assertEquals(List.of(), report.changes());
        assertEquals("270/manual", match("Hackfleisch gem."));
        assertEquals(DUSCHGEL + "/manual", match("BACON-STREIFEN"));
    }

    @Test
    void aKeyMovedToReviewClearsTheFormerOverrideMatch() throws SQLException {
        assertEquals("1536/override", match("BACON-STREIFEN"));
        Map<String, Integer> exact = new HashMap<>(OVERRIDES.exact());
        exact.remove("bacon streifen");
        Set<String> review = new HashSet<>(OVERRIDES.review());
        review.add("bacon streifen");

        ReceiptRematchService.ReceiptRematchReport report = rematch(new ReceiptMatcher.Overrides(exact, review), true);

        assertEquals(1, report.nowUnmatched());
        assertEquals("null/null", match("BACON-STREIFEN"));
        assertTrue(report.toText().contains("1536 Speckstreifen (override) -> unmatched"), report.toText());
    }

    @Test
    void dryRunWritesNothing() throws SQLException {
        String before = lines();

        ReceiptRematchService.ReceiptRematchReport report = rematch(with("duschgel sport", DUSCHGEL), false);

        assertFalse(report.committed());
        assertEquals(1, report.changes().size());
        assertTrue(report.toText().contains("DRY RUN"), report.toText());
        assertEquals(before, lines());
    }

    @Test
    void anErrorRollsBackEveryChange() throws SQLException {
        String before = lines();
        ReceiptRepository failing = new ReceiptRepository() {
            private int updates;

            @Override
            public void updateMatch(Connection con, ReceiptRematchService.Change c) throws SQLException {
                if (++updates == 2) {
                    throw new SQLException("simulated failure on the second change");
                }
                super.updateMatch(con, c);
            }
        };
        Map<String, Integer> exact = new HashMap<>(OVERRIDES.exact());
        exact.put("duschgel sport", DUSCHGEL);
        exact.put("bananen", 0);

        assertThrows(RuntimeException.class, () -> new ReceiptRematchService(failing, new StoreRepository()::loadCatalog,
                new ReceiptMatcher.Overrides(exact, OVERRIDES.review())).run(true));

        assertEquals(before, lines());
    }

    @Test
    void itNeverTouchesReceiptsAdjustmentsStockOrPurchaseHistory() throws SQLException {
        exec("UPDATE inventory_item SET inventory_item_quantity = 3 WHERE inventory_item_id = 356");
        String receipts = table("SELECT CONCAT_WS('|', purchase_receipt_id, purchase_receipt_store_id, purchase_receipt_purchased_at, "
                + "purchase_receipt_receipt_number, purchase_receipt_total, purchase_receipt_app_savings, "
                + "purchase_receipt_other_savings, purchase_receipt_loyalty_points, purchase_receipt_imported_at) "
                + "FROM purchase_receipt ORDER BY purchase_receipt_id");
        String adjustments = table("SELECT CONCAT_WS('|', purchase_receipt_adjustment_id, purchase_receipt_adjustment_line_id, "
                + "purchase_receipt_adjustment_type, purchase_receipt_adjustment_amount, purchase_receipt_adjustment_raw_text) "
                + "FROM purchase_receipt_adjustment ORDER BY purchase_receipt_adjustment_id");
        String stock = table("SELECT CONCAT_WS('|', inventory_item_id, inventory_item_quantity, inventory_item_unit, "
                + "inventory_item_name_deutsch) FROM inventory_item ORDER BY inventory_item_id");

        rematch(with("duschgel sport", DUSCHGEL), true);

        assertEquals(DUSCHGEL + "/override", match("DUSCHGEL SPORT"));
        assertEquals(receipts, table("SELECT CONCAT_WS('|', purchase_receipt_id, purchase_receipt_store_id, "
                + "purchase_receipt_purchased_at, purchase_receipt_receipt_number, purchase_receipt_total, "
                + "purchase_receipt_app_savings, purchase_receipt_other_savings, purchase_receipt_loyalty_points, "
                + "purchase_receipt_imported_at) FROM purchase_receipt ORDER BY purchase_receipt_id"));
        assertEquals(adjustments, table("SELECT CONCAT_WS('|', purchase_receipt_adjustment_id, purchase_receipt_adjustment_line_id, "
                + "purchase_receipt_adjustment_type, purchase_receipt_adjustment_amount, purchase_receipt_adjustment_raw_text) "
                + "FROM purchase_receipt_adjustment ORDER BY purchase_receipt_adjustment_id"));
        assertEquals(stock, table("SELECT CONCAT_WS('|', inventory_item_id, inventory_item_quantity, inventory_item_unit, "
                + "inventory_item_name_deutsch) FROM inventory_item ORDER BY inventory_item_id"));
        assertEquals("0", table("SELECT COUNT(*) FROM purchase_history"));
    }

    @Test
    void anOverrideToAMissingItemIsRefusedBeforeAnythingIsWritten() throws SQLException {
        String before = lines();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> rematch(with("duschgel sport", 999999), true));

        assertTrue(e.getMessage().contains("duschgel sport -> 999999"), e.getMessage());
        assertEquals(before, lines());
    }

    // ---------- helpers ----------

    private static ReceiptRematchService.ReceiptRematchReport rematch(ReceiptMatcher.Overrides overrides, boolean commit) {
        return new ReceiptRematchService(overrides).run(commit);
    }

    /** The real overrides plus one more exact entry. */
    private static ReceiptMatcher.Overrides with(String key, int itemId) {
        Map<String, Integer> exact = new HashMap<>(OVERRIDES.exact());
        exact.put(key, itemId);
        return new ReceiptMatcher.Overrides(exact, OVERRIDES.review());
    }

    private static ReceiptImportService.ReceiptFile file(String name) {
        try (InputStream in = ReceiptRematchTest.class.getResourceAsStream("/receipts/import/" + name)) {
            assertNotNull(in, name);
            return new ReceiptImportService.ReceiptFile(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "item/source" of the stored line with this raw name. */
    private static String match(String rawName) throws SQLException {
        try (Connection con = Database.getConnection();
             var ps = con.prepareStatement("SELECT purchase_receipt_line_inventory_item_id, purchase_receipt_line_match_source "
                     + "FROM purchase_receipt_line WHERE purchase_receipt_line_raw_name = ?")) {
            ps.setString(1, rawName);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "no stored line " + rawName);
                return rs.getString(1) + "/" + rs.getString(2);
            }
        }
    }

    /** Every stored line, all columns. */
    private static String lines() throws SQLException {
        return table("SELECT CONCAT_WS('|', purchase_receipt_line_id, purchase_receipt_line_raw_name, "
                + "COALESCE(purchase_receipt_line_brand, '-'), COALESCE(purchase_receipt_line_inventory_item_id, '-'), "
                + "COALESCE(purchase_receipt_line_match_source, '-'), purchase_receipt_line_paid_total) "
                + "FROM purchase_receipt_line ORDER BY purchase_receipt_line_id");
    }

    private static String table(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return String.join(";", out);
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
