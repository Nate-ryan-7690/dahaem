package de.dahaem.stores;

import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The store and brand lists for pickers, and the lookups that never create anything. */
class StoreCatalogServiceTest {

    private StoreRepository repository;
    private StoreCatalogService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        repository = new StoreRepository();
        service = new StoreCatalogService(repository);
        exec("INSERT INTO store (store_name) VALUES ('Rewe'), ('Aldi'), ('Penny')");
        exec("INSERT INTO brand (brand_name) VALUES ('Barilla'), ('Bio 50% Frisch'), ('Ja!'), ('Gut Bio'), ('Gut & Günstig')");
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void storesAreListedByName() {
        List<StoreRef> stores = service.stores();

        assertEquals(List.of("Aldi", "Penny", "Rewe"), stores.stream().map(StoreRef::name).toList());
    }

    @Test
    void brandsAreSearchedAsASubstringPagedAndByName() {
        BrandPage all = service.brands(null, 0, 3);
        assertEquals(5, all.total());
        assertEquals(List.of("Barilla", "Bio 50% Frisch", "Gut & Günstig"), all.items().stream().map(BrandRef::name).toList());

        assertEquals(List.of("Ja!"), service.brands("ja", 0, 10).items().stream().map(BrandRef::name).toList());
        assertEquals(2, service.brands("gut", 0, 10).total());
        assertEquals(List.of("Gut Bio"), service.brands("gut", 1, 10).items().stream().map(BrandRef::name).toList(), "second of two");
        assertEquals(0, service.brands("zzz", 0, 10).total());
        assertTrue(service.brands("  ", 0, 10).total() == 5, "a blank query means all");
    }

    @Test
    void wildcardsInTheSearchTextAreLiteral() {
        assertEquals(List.of("Bio 50% Frisch"), service.brands("50%", 0, 10).items().stream().map(BrandRef::name).toList());
        assertEquals(0, service.brands("%", 0, 10).items().stream().filter(b -> b.name().equals("Barilla")).count(),
                "'%' means a percent sign, not 'anything'");
    }

    @Test
    void lookupsFindWhateverTheCaseAndCreateNothing() {
        assertEquals("Aldi", repository.findStoreByName("aLDI").orElseThrow().name());
        assertTrue(repository.findStoreByName("Aldii").isEmpty());
        assertEquals("Barilla", repository.findBrandByName("BARILLA").orElseThrow().name());
        assertTrue(repository.findBrandByName("Barilaa").isEmpty());
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
