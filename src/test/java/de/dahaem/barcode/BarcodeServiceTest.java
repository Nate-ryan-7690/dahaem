package de.dahaem.barcode;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.shared.Database;
import de.dahaem.stores.StoreRepository;
import de.dahaem.stores.UnknownBrandException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Barcodes on a throwaway MariaDB: lookup order, suggestions, assigning, linking store items, rolling back. */
class BarcodeServiceTest {

    private static final String CODE = "4006381333931";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private ProductBarcodeRepository repository;
    private FakeSource source;
    private BarcodeService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        repository = new ProductBarcodeRepository();
        source = new FakeSource();
        service = new BarcodeService(repository, new InventoryRepository(), new StoreRepository(), source);
        exec("INSERT INTO store (store_id, store_name) VALUES (1, 'Aldi'), (2, 'Penny')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (1, 'TestBrand')");
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_quantity, inventory_item_unit, inventory_item_category) VALUES "
                + "(1, 'spaghetti', 'spaghetti', 0, 'g', 'pasta'), (2, 'rice', 'reis', 0, 'g', 'grain'), "
                + "(3, 'milk', 'milch', 0, 'ml', 'dairy'), (4, 'detergent', 'waschmittel', 0, 'WL', 'household'), "
                + "(5, 'eggs', 'eier', 0, 'Stück', 'dairy')");
        // store items: Aldi and Penny both carry spaghetti (item 1); Aldi also has rice (item 2)
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, "
                + "store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available) VALUES "
                + "(1, 1, 1, 1, 'g', 500, 1.29, 1), (2, 1, 2, 1, 'g', 500, 1.19, 1), (3, 1, 1, 2, 'g', 1000, 2.49, 1)");
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void anUnknownCodeWithTheOutsideLookupOffIsDisabledAndSendsNothing() {
        source.enabled = false;

        BarcodeLookup lookup = service.lookup(CODE);

        assertEquals("DISABLED", lookup.status());
        assertEquals(0, source.calls);
    }

    @Test
    void anUnknownCodeGetsASuggestionThatIsNotSaved() {
        source.result = Optional.of(new ProductSuggestion("Spaghetti", "Barilla", 500, "g", "500 g", "openfoodfacts"));

        BarcodeLookup lookup = service.lookup(CODE);

        assertEquals("FOUND", lookup.status());
        assertEquals("Spaghetti", lookup.suggestion().name());
        assertNull(lookup.product());
        assertTrue(repository.findByCode(CODE).isEmpty(), "a suggestion must not be saved by itself");
    }

    @Test
    void notFoundAndUnavailableAreTold() {
        source.result = Optional.empty();
        assertEquals("NOT_FOUND", service.lookup(CODE).status());

        source.failure = new IOException("down");
        assertEquals("UNAVAILABLE", service.lookup(CODE).status());
    }

    @Test
    void anAssignedCodeIsKnownAndTheOutsideSourceIsNotAskedAgain() {
        BarcodeLookup assigned = service.assign(CODE, 1, "Barilla", 500, "g", "Spaghetti n. 5", null, TODAY);
        assertEquals("KNOWN", assigned.status());

        source.failure = new IOException("must not be called");
        BarcodeLookup lookup = service.lookup(CODE);

        assertEquals("KNOWN", lookup.status());
        assertEquals(0, source.calls);
        BarcodeProduct p = lookup.product();
        assertEquals(1, p.inventoryItemId());
        assertEquals("spaghetti", p.nameEnglish());
        assertEquals("Barilla", p.brand());
        assertEquals(500, p.packSize());
        assertEquals("g", p.packUnit());
        assertEquals("Spaghetti n. 5", p.name());
        assertTrue(p.storeItems().isEmpty());
    }

    @Test
    void aTwelveDigitUpcAndItsThirteenDigitFormAreTheSameBarcode() {
        service.assign("036000291452", 1, null, 500, "g", null, null, TODAY);

        BarcodeProduct p = service.lookup("0036000291452").product();

        assertEquals("0036000291452", p.code());
        assertNull(p.brand());
    }

    @Test
    void storeItemsOfSeveralStoresCanCarryTheSameBarcode() {
        service.assign(CODE, 1, "TestBrand", 500, "g", null, 1, TODAY);
        BarcodeProduct p = service.assign(CODE, 1, "TestBrand", 500, "g", null, 2, TODAY).product();

        assertEquals(2, p.storeItems().size());
        assertEquals("Aldi", p.storeItems().get(0).storeName());
        assertEquals(1.29, p.storeItems().get(0).price(), 0.001);
        assertEquals("Penny", p.storeItems().get(1).storeName());
        // linking the same store item again changes nothing
        assertEquals(2, service.assign(CODE, 1, "TestBrand", 500, "g", null, 2, TODAY).product().storeItems().size());
    }

    @Test
    void assigningAgainReplacesWhatTheBarcodeIs() {
        service.assign(CODE, 1, "Barilla", 500, "g", null, null, TODAY);

        BarcodeProduct p = service.assign(CODE, 2, null, 1000, "g", null, null, TODAY).product();

        assertEquals(2, p.inventoryItemId());
        assertEquals(1000, p.packSize());
        assertNull(p.brand());
    }

    @Test
    void thePackIsStoredInTheUnitsTheShoppingListUses() {
        BarcodeProduct rice = service.assign(CODE, 2, null, 1, "kg", null, null, TODAY).product();
        assertEquals(1000, rice.packSize());
        assertEquals("g", rice.packUnit());

        BarcodeProduct milk = service.assign("73513537", 3, null, 2, "L", null, null, TODAY).product();
        assertEquals(2000, milk.packSize());
        assertEquals("ml", milk.packUnit());

        BarcodeProduct eggs = service.assign("4012345678901", 5, null, 10, "Stück", null, null, TODAY).product();
        assertEquals(10, eggs.packSize());
        assertEquals("stück", eggs.packUnit());

        BarcodeProduct detergent = service.assign("4006381333948", 4, null, 20, "wl", null, null, TODAY).product();
        assertEquals(20, detergent.packSize());
        assertEquals("WL", detergent.packUnit());
    }

    @Test
    void aPackThatCannotBeStockedInTheItemsUnitIsRefused() {
        // ml for an item counted in g, g for an item counted in ml, pieces for an item in g, an unknown unit for a g item
        assertThrows(PackUnitException.class, () -> service.assign(CODE, 1, null, 500, "ml", null, null, TODAY));
        assertThrows(PackUnitException.class, () -> service.assign(CODE, 3, null, 500, "g", null, null, TODAY));
        assertThrows(PackUnitException.class, () -> service.assign(CODE, 1, null, 6, "stück", null, null, TODAY));
        assertThrows(PackUnitException.class, () -> service.assign(CODE, 1, null, 500, "Eimer", null, null, TODAY));
        assertTrue(repository.findByCode(CODE).isEmpty());
    }

    @Test
    void differentPackSizesOfTheSameProductAreDifferentBarcodes() {
        service.assign(CODE, 1, "Barilla", 500, "g", null, 1, TODAY);
        service.assign("73513537", 1, "Barilla", 250, "g", null, null, TODAY);
        service.assign("4012345678901", 1, "Barilla", 550, "g", "Aktion +50 g", null, TODAY);

        assertEquals(500, service.lookup(CODE).product().packSize());
        assertEquals(250, service.lookup("73513537").product().packSize());
        assertEquals(550, service.lookup("4012345678901").product().packSize());
    }

    @Test
    void anUnknownBrandIsRefusedUnlessAskedAndAnExistingOneMatchesWhateverTheCase() throws SQLException {
        assertThrows(UnknownBrandException.class,
                () -> service.assign(CODE, 1, "Barilaa", 500, "g", null, null, false, TODAY));
        assertEquals(0, count("SELECT COUNT(*) FROM brand WHERE brand_name = 'Barilaa'"), "a typo must not add a brand");
        assertTrue(repository.findByCode(CODE).isEmpty());

        BarcodeProduct existing = service.assign(CODE, 1, "testbrand", 500, "g", null, null, false, TODAY).product();
        assertEquals("TestBrand", existing.brand());
        assertEquals(1, count("SELECT COUNT(*) FROM brand"), "'testbrand' is the existing TestBrand");

        BarcodeProduct created = service.assign("73513537", 1, "Barilla", 250, "g", null, null, true, TODAY).product();
        assertEquals("Barilla", created.brand());
        assertEquals(2, count("SELECT COUNT(*) FROM brand"));
    }

    @Test
    void badInputIsRefusedAndNothingIsSaved() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> service.assign(CODE, 99, null, 500, "g", null, null, TODAY));   // no such item
        assertThrows(IllegalArgumentException.class, () -> service.assign(CODE, 1, null, 0, "g", null, null, TODAY));      // pack size
        assertThrows(IllegalArgumentException.class, () -> service.assign(CODE, 1, null, 500, " ", null, null, TODAY));    // pack unit
        assertThrows(IllegalArgumentException.class, () -> service.assign(CODE, 1, "NewBrand", 500, "g", null, 999, TODAY)); // no such store item
        assertThrows(IllegalArgumentException.class, () -> service.assign(CODE, 1, "NewBrand", 500, "g", null, 3, TODAY));   // store item of another item
        assertThrows(InvalidBarcodeException.class, () -> service.assign("4006381333932", 1, null, 500, "g", null, null, TODAY));

        assertTrue(repository.findByCode(CODE).isEmpty());
        assertEquals(0, count("SELECT COUNT(*) FROM brand WHERE brand_name = 'NewBrand'"),
                "a refused request must not leave a brand behind");
    }

    // ---------- helpers ----------

    private static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

    /** A data source that answers what the test says and counts how often it was asked. */
    private static class FakeSource implements ProductDataSource {
        boolean enabled = true;
        Optional<ProductSuggestion> result = Optional.empty();
        IOException failure;
        int calls;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Optional<ProductSuggestion> find(String code) throws IOException {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
