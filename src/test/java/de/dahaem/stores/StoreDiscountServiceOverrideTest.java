package de.dahaem.stores;

import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Override handling in the discounts step against a throwaway MariaDB: full-name lookup and "0 = excluded". */
class StoreDiscountServiceOverrideTest {

    private final StoreRepository repository = new StoreRepository();

    @BeforeEach
    void setUp() throws SQLException {
        TestDatabase.reset();
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_quantity, inventory_item_unit, inventory_item_category) VALUES "
                + "(1, 'butter', 'butter', 0, 'g', 'dairy'), (2, 'ice cream (stick)', 'Stieleis', 0, 'ml', 'frozen')");
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    private static StoreOffer offer(String id, String brand, String name, String unit, double price) {
        LocalDate from = LocalDate.now();
        return new StoreOffer(id, brand, name, unit, price, 1, null, null, null, null, null, null, "", "",
                from, from.plusDays(6));
    }

    @Test
    void anOverrideOfZeroExcludesTheOfferWithoutAbortingTheRun(@TempDir Path dir) throws Exception {
        int store = repository.findOrCreateStore("Lidl");
        repository.saveOffers(store, List.of(
                offer("A", null, "Butter", "250 g", 1.99),
                offer("B", null, "Mystery Bundle", "500 g", 2.49)), LocalDate.now(), "AGGREGATOR");
        StoreDiscountService service = new StoreDiscountService(repository,
                Map.of(Normalizer.key("Mystery Bundle"), 0));

        ImportSummary summary = service.promoteOffers(null, dir.resolve("unmatched.tsv"));

        assertEquals("OK", summary.status());
        assertEquals(1, count("SELECT COUNT(*) FROM store_item"), "only the butter offer is promoted");
        assertEquals(1, count("SELECT COUNT(*) FROM store_discount"));
        assertTrue(summary.notes().contains("1 excluded by override"), summary.notes());
        assertTrue(summary.notes().contains("0 unmatched"), summary.notes());
        assertFalse(Files.readString(dir.resolve("unmatched.tsv")).contains("Mystery Bundle"),
                "an excluded offer is left out on purpose and is not in the unmatched report");
    }

    @Test
    void anOverrideOnTheFullNameFiresEvenWhenTheFirstWordIsAKnownBrand(@TempDir Path dir) throws Exception {
        int store = repository.findOrCreateStore("Aldi");
        // an earlier offer from the store itself makes "Mars" a known brand
        repository.saveOffers(store, List.of(offer("S1", "Mars", "Twix", "50 g", 0.5)), LocalDate.now(), "STORE");
        repository.saveOffers(store, List.of(offer("A1", null, "Mars Ice Cream", "5 x 50 ml", 2.22)), LocalDate.now(), "AGGREGATOR");
        StoreDiscountService service = new StoreDiscountService(repository,
                Map.of(Normalizer.key("Mars Ice Cream"), 2, Normalizer.key("Twix"), 0));

        service.promoteOffers(null, dir.resolve("unmatched.tsv"));

        assertEquals(1, count("SELECT COUNT(*) FROM store_item WHERE store_item_item_id = 2"),
                "'Mars Ice Cream' resolved through its full-name override, not as the bare 'Ice Cream'");
    }

    private static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
