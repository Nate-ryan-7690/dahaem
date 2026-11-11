package de.dahaem.barcode;

import de.dahaem.finance.PurchaseRepository;
import de.dahaem.finance.PurchaseService;
import de.dahaem.finance.StockBatch;
import de.dahaem.finance.StockService;
import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.Item;
import de.dahaem.shared.Database;
import de.dahaem.stores.StoreRepository;
import de.dahaem.stores.UnknownStoreException;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Putting groceries away by barcode on a throwaway MariaDB: stock in the item's unit, batches, store items, partial failure. */
class IntakeServiceTest {

    private static final String SPAGHETTI = "4006381333931";
    private static final String MILK = "73513537";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private IntakeService intake;
    private StockService stock;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        PurchaseRepository purchaseRepository = new PurchaseRepository();
        StoreRepository stores = new StoreRepository();
        InventoryRepository inventory = new InventoryRepository();
        ProductBarcodeRepository barcodes = new ProductBarcodeRepository();
        PurchaseService purchases = new PurchaseService(purchaseRepository, stores, inventory);
        intake = new IntakeService(barcodes, purchases, stores, new IntakeRequestRepository());
        stock = new StockService(purchaseRepository, inventory);
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_quantity, inventory_item_unit, inventory_item_category) VALUES "
                + "(1, 'spaghetti', 'spaghetti', 0, 'g', 'pasta'), (2, 'milk', 'milch', 0, 'ml', 'dairy')");
        BarcodeService barcodeService = new BarcodeService(barcodes, inventory, stores, ProductDataSource.disabled());
        barcodeService.assign(SPAGHETTI, 1, "Barilla", 500, "g", "Spaghetti n. 5", null, TODAY);
        barcodeService.assign(MILK, 2, null, 1, "l", null, null, TODAY);
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void stockGrowsByPacksTimesPackSizeInTheItemsUnitAndTheStoreItemIsLinked() throws SQLException {
        IntakeService.LineResult r = intake.intake("Aldi", true, TODAY, List.of(
                new IntakeService.Line(SPAGHETTI, 2, LocalDate.of(2026, 12, 1), 2.58, null, null))).get(0);

        assertTrue(r.ok(), r.message());
        assertEquals(1000, r.stockAdded());
        assertEquals("g", r.stockUnit());
        assertEquals(1000, count("SELECT inventory_item_quantity FROM inventory_item WHERE inventory_item_id = 1"));
        assertEquals("2026-12-01", text("SELECT inventory_item_earliest_exp_date FROM inventory_item WHERE inventory_item_id = 1"));
        // the store item for (Aldi, Barilla, spaghetti, 500 g) exists and carries the barcode
        assertEquals(1, count("SELECT COUNT(*) FROM store_item WHERE store_item_package_size = 500 AND store_item_package_unit = 'g'"));
        assertEquals(1, count("SELECT COUNT(*) FROM product_barcode_store_item WHERE product_barcode_store_item_code = '"
                + SPAGHETTI + "'"));
        assertEquals(2, count("SELECT purchase_history_quantity_purchased FROM purchase_history"));
        assertEquals(1, count("SELECT COUNT(*) FROM purchase_history WHERE purchase_history_quantity_remaining IS NULL"));
    }

    @Test
    void useTakesFromTheEarliestExpiringBatchInGramsAndMovesTheDateOn() throws SQLException {
        // 2 packs (1000 g) expiring 1 Dec, 1 pack (500 g) expiring 10 Dec
        intake.intake("Aldi", true, TODAY, List.of(
                new IntakeService.Line(SPAGHETTI, 2, LocalDate.of(2026, 12, 1), 2.58, null, null),
                new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 10), 1.29, null, null)));

        Item item = stock.use(1, 1200).orElseThrow();

        assertEquals(300, item.getQuantity());
        assertEquals(LocalDate.of(2026, 12, 10), item.getEarliestExpDate());
        assertEquals(0, count("SELECT purchase_history_quantity_remaining FROM purchase_history WHERE purchase_history_expiration_date = '2026-12-01'"));
        assertEquals(300, count("SELECT purchase_history_quantity_remaining FROM purchase_history WHERE purchase_history_expiration_date = '2026-12-10'"));
    }

    @Test
    void aLinePriceIsOptionalWhenTheStoreItemHasAKnownPrice() throws SQLException {
        intake.intake("Aldi", true, TODAY, List.of(new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 1), 1.29, null, null)));
        exec("UPDATE store_item SET store_item_price = 1.29");

        IntakeService.LineResult r = intake.intake("Aldi", true, TODAY, List.of(
                new IntakeService.Line(SPAGHETTI, 3, LocalDate.of(2026, 12, 5), null, null, null))).get(0);

        assertTrue(r.ok(), r.message());
        assertEquals(1, count("SELECT COUNT(*) FROM purchase_history WHERE purchase_history_price_paid = 3.87"));
    }

    @Test
    void withoutAKnownPriceTheLineNeedsOneAndNothingIsCreated() throws SQLException {
        IntakeService.LineResult r = intake.intake("Lidl", true, TODAY, List.of(
                new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 1), null, null, null))).get(0);

        assertFalse(r.ok());
        assertEquals("PRICE_REQUIRED", r.code());
        assertEquals(0, count("SELECT COUNT(*) FROM store"), "a refused line must not create a store");
        assertEquals(0, count("SELECT COUNT(*) FROM store_item"));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_history"));
    }

    @Test
    void aSpecialPackIsItsOwnStoreItemAndAddsItsOwnSize() throws SQLException {
        IntakeService.LineResult r = intake.intake("Aldi", true, TODAY, List.of(
                new IntakeService.Line(SPAGHETTI, 2, LocalDate.of(2026, 12, 1), 2.98, 550, "g"))).get(0);

        assertTrue(r.ok(), r.message());
        assertEquals(1100, r.stockAdded());
        assertEquals(1, count("SELECT COUNT(*) FROM store_item WHERE store_item_package_size = 550"));
        assertEquals(1100, count("SELECT inventory_item_quantity FROM inventory_item WHERE inventory_item_id = 1"));
    }

    @Test
    void oneBadLineDoesNotStopTheOthers() throws SQLException {
        List<IntakeService.LineResult> results = intake.intake("Aldi", true, TODAY, List.of(
                new IntakeService.Line("4006381333932", 1, LocalDate.of(2026, 12, 1), 1.0, null, null),   // typo
                new IntakeService.Line("4012345678901", 1, LocalDate.of(2026, 12, 1), 1.0, null, null),   // not assigned
                new IntakeService.Line(SPAGHETTI, 1, null, 1.0, null, null),                              // no expiry
                new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 1), 1.0, 500, "ml"),          // unit mismatch
                new IntakeService.Line(MILK, 2, LocalDate.of(2026, 11, 20), 2.38, null, null)));          // fine

        assertEquals("BARCODE_CHECKSUM", results.get(0).code());
        assertEquals("BARCODE_UNKNOWN", results.get(1).code());
        assertEquals("EXPIRY_REQUIRED", results.get(2).code());
        assertEquals("PACK_UNIT_MISMATCH", results.get(3).code());
        assertTrue(results.get(4).ok());
        assertEquals(2000, results.get(4).stockAdded());
        assertEquals("ml", results.get(4).stockUnit());
        assertEquals(2000, count("SELECT inventory_item_quantity FROM inventory_item WHERE inventory_item_id = 2"));
        assertEquals(0, count("SELECT inventory_item_quantity FROM inventory_item WHERE inventory_item_id = 1"));
        assertNull(results.get(0).purchaseId());
    }

    @Test
    void anUnknownStoreIsRefusedUnlessAskedAndTheCaseOfAnExistingOneDoesNotMatter() throws SQLException {
        IntakeService.Line line = new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 1), 1.29, null, null);
        intake.intake("Aldi", true, TODAY, List.of(line));   // asked to create it: the store now exists

        assertThrows(UnknownStoreException.class, () -> intake.intake("Aldii", false, TODAY, List.of(line)));
        assertEquals(1, count("SELECT COUNT(*) FROM store"), "a refused store must not be created");
        assertEquals(1, count("SELECT COUNT(*) FROM purchase_history"), "and nothing is recorded for it");

        IntakeService.LineResult r = intake.intake("  aldi ", false, TODAY, List.of(line)).get(0);
        assertTrue(r.ok(), r.message());
        assertEquals(1, count("SELECT COUNT(*) FROM store"), "'aldi' is the existing Aldi, not a new store");
        assertEquals(2, count("SELECT COUNT(*) FROM purchase_history"));
    }

    @Test
    void batchesListWhatIsLeftOfEachPurchaseInUseOrder() {
        intake.intake("Aldi", true, TODAY, List.of(
                new IntakeService.Line(SPAGHETTI, 2, LocalDate.of(2026, 12, 1), 2.58, null, null),
                new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 10), 1.29, null, null)));

        List<StockBatch> batches = stock.batches(1, false).orElseThrow();

        assertEquals(2, batches.size());
        StockBatch first = batches.get(0);
        assertEquals(LocalDate.of(2026, 12, 1), first.expirationDate());
        assertEquals("Aldi", first.storeName());
        assertEquals("Barilla", first.brandName());
        assertEquals(TODAY, first.purchaseDate());
        assertEquals(2, first.packs());
        assertEquals(500, first.packSize());
        assertEquals(1000, first.remaining());
        assertEquals("g", first.unit());
        assertEquals(500, batches.get(1).remaining());

        stock.use(1, 1200);   // the first batch is used up, 300 g are left of the second

        List<StockBatch> left = stock.batches(1, false).orElseThrow();
        assertEquals(1, left.size());
        assertEquals(300, left.get(0).remaining());
        List<StockBatch> all = stock.batches(1, true).orElseThrow();
        assertEquals(2, all.size());
        assertEquals(0, all.get(0).remaining());
        assertTrue(stock.batches(999, false).isEmpty(), "no such item");
    }

    @Test
    void aRepeatedRequestIdGetsTheStoredAnswerAndIsNotRecordedTwice() throws SQLException {
        List<IntakeService.Line> lines = List.of(
                new IntakeService.Line(SPAGHETTI, 2, LocalDate.of(2026, 12, 1), 2.58, null, null),
                new IntakeService.Line("4006381333932", 1, LocalDate.of(2026, 12, 1), 1.0, null, null));

        IntakeService.Outcome first = intake.intakeOnce("req-0000001", "body-1", "Aldi", true, TODAY, lines);
        IntakeService.Outcome again = intake.intakeOnce("req-0000001", "body-1", "Aldi", true, TODAY, lines);

        assertFalse(first.replayed());
        assertTrue(again.replayed());
        assertEquals(first.results(), again.results(), "the same answer, including the failed line");
        assertEquals(1, count("SELECT COUNT(*) FROM purchase_history"), "recorded once");
        assertEquals(1000, count("SELECT inventory_item_quantity FROM inventory_item WHERE inventory_item_id = 1"));
    }

    @Test
    void theSameRequestIdWithAnotherBodyOrWhileProcessingIsAConflict() {
        List<IntakeService.Line> lines = List.of(
                new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 1), 1.29, null, null));
        intake.intakeOnce("req-0000002", "body-1", "Aldi", true, TODAY, lines);

        IntakeConflictException reused = assertThrows(IntakeConflictException.class,
                () -> intake.intakeOnce("req-0000002", "body-2", "Aldi", true, TODAY, lines));
        assertEquals("REQUEST_ID_REUSED", reused.code());

        // a claim without an answer yet = a request still being processed
        new IntakeRequestRepository().claim("req-0000003", sha("body-3"));
        IntakeConflictException busy = assertThrows(IntakeConflictException.class,
                () -> intake.intakeOnce("req-0000003", "body-3", "Aldi", true, TODAY, lines));
        assertEquals("REQUEST_IN_PROGRESS", busy.code());
    }

    @Test
    void aRequestThatFailsBeforeRecordingGivesItsIdBackSoTheCorrectedOneCanUseIt() throws SQLException {
        List<IntakeService.Line> lines = List.of(
                new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 1), 1.29, null, null));
        intake.intakeOnce("req-0000004", "body-4", "Aldi", true, TODAY, lines);   // makes the store Aldi exist

        assertThrows(UnknownStoreException.class,
                () -> intake.intakeOnce("req-0000005", "body-5", "Aldii", false, TODAY, lines));
        IntakeService.Outcome fixed = intake.intakeOnce("req-0000005", "body-5", "Aldi", false, TODAY, lines);

        assertFalse(fixed.replayed(), "the failed attempt did not keep the id");
        assertTrue(fixed.results().get(0).ok());
        assertEquals(2, count("SELECT COUNT(*) FROM purchase_history"));
    }

    @Test
    void withoutARequestIdItIsJustAnIntake() throws SQLException {
        List<IntakeService.Line> lines = List.of(
                new IntakeService.Line(SPAGHETTI, 1, LocalDate.of(2026, 12, 1), 1.29, null, null));

        intake.intakeOnce(null, "x", "Aldi", true, TODAY, lines);
        intake.intakeOnce(null, "x", "Aldi", true, TODAY, lines);

        assertEquals(2, count("SELECT COUNT(*) FROM purchase_history"));
        assertEquals(0, count("SELECT COUNT(*) FROM intake_request"));
    }

    // ---------- helpers ----------

    private static String sha(String text) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String text(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
