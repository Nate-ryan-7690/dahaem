package de.dahaem.barcode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading an Open Food Facts answer (no network). */
class OpenFoodFactsSourceTest {

    @Test
    void readsNameFirstBrandAndPackFromTheQuantityText() throws IOException {
        ProductSuggestion s = OpenFoodFactsSource.parse(
                "{\"status\":1,\"product\":{\"product_name\":\"Spaghetti n. 5\",\"brands\":\"Barilla, Foo\",\"quantity\":\"500 g\"}}")
                .orElseThrow();

        assertEquals("Spaghetti n. 5", s.name());
        assertEquals("Barilla", s.brand());
        assertEquals(500, s.packSize());
        assertEquals("g", s.packUnit());
        assertEquals("500 g", s.quantityText());
        assertEquals("openfoodfacts", s.source());
    }

    @Test
    void convertsKilogramsAndLitresToTheBaseUnit() throws IOException {
        ProductSuggestion kilo = OpenFoodFactsSource.parse(
                "{\"status\":1,\"product\":{\"product_name\":\"Reis\",\"quantity\":\"1 kg\"}}").orElseThrow();
        assertEquals(1000, kilo.packSize());
        assertEquals("g", kilo.packUnit());
        assertNull(kilo.brand());

        ProductSuggestion milk = OpenFoodFactsSource.parse(
                "{\"status\":1,\"product\":{\"product_name\":\"Milch\",\"quantity\":\"1,5 l\"}}").orElseThrow();
        assertEquals(1500, milk.packSize());
        assertEquals("ml", milk.packUnit());
    }

    @Test
    void fallsBackToTheNumericQuantityFields() throws IOException {
        ProductSuggestion s = OpenFoodFactsSource.parse(
                "{\"status\":1,\"product\":{\"product_name\":\"Mehl\",\"product_quantity\":1000,\"product_quantity_unit\":\"g\"}}")
                .orElseThrow();
        assertEquals(1000, s.packSize());
        assertEquals("g", s.packUnit());
    }

    @Test
    void aProductWithoutAReadableQuantityStillGivesNameAndBrand() throws IOException {
        ProductSuggestion s = OpenFoodFactsSource.parse(
                "{\"status\":1,\"product\":{\"product_name\":\"Senf\",\"brands\":\"Löwensenf\"}}").orElseThrow();
        assertEquals("Senf", s.name());
        assertNull(s.packSize());
        assertNull(s.packUnit());
    }

    @Test
    void unknownProductsAndProductsWithoutANameAreEmpty() throws IOException {
        assertEquals(Optional.empty(), OpenFoodFactsSource.parse("{\"status\":0,\"status_verbose\":\"product not found\"}"));
        assertTrue(OpenFoodFactsSource.parse("{\"status\":1,\"product\":{\"brands\":\"X\"}}").isEmpty());
    }
}
