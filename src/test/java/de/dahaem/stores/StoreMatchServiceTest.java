package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreMatchServiceTest {

    private static CatalogItem item(int id, String english, String deutsch) {
        return new CatalogItem(id, english, deutsch, Normalizer.key(english), Normalizer.key(deutsch));
    }

    @Test
    void overrideWins() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"));
        // The override maps a normalized store name to a *different* catalog id than the
        // exact/contains rules would pick, so we can tell the override actually short-circuited.
        Map<String, Integer> overrides = Map.of(Normalizer.key("MILSANI Streichfein"), 42);
        StoreMatchService service = new StoreMatchService(null, overrides);

        assertEquals(42, service.match("MILSANI Streichfein", catalog));
    }

    @Test
    void exactMatchOnNormalizedName() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"), item(2, "milk", "milch"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertEquals(1, service.match("Butter", catalog));
    }

    @Test
    void longestWholeWordContainsWins() {
        List<CatalogItem> catalog = List.of(item(1, "oil", "oel"), item(2, "rapeseed oil", "rapsoel"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        // "Bio Rapsöl" normalizes to "bio rapsoel"; both "oel" and "rapsoel" appear as whole
        // words, but "rapsoel" is longer and should win.
        assertEquals(2, service.match("Bio Rapsöl", catalog));
    }

    @Test
    void tieBetweenEquallyLongCandidatesLeavesUnmatched() {
        // Two catalog names of the same length that both match as whole words in the store name.
        List<CatalogItem> tiedCatalog = List.of(
                item(10, "aaa", "abc"),
                item(11, "bbb", "xyz"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertNull(service.match("abc xyz product", tiedCatalog));
    }

    @Test
    void noMatchReturnsNull() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertNull(service.match("Kaffee komplett anders", catalog));
    }

    @Test
    void emptyNormalizedNameReturnsNull() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertNull(service.match("", catalog));
    }
}
