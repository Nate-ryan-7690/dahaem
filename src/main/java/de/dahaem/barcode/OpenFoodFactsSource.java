package de.dahaem.barcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dahaem.stores.StorePromotionService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Open Food Facts (open data, no key): name, brand and quantity of a product by its barcode. Only the barcode number is
 * sent, with a generic client name. Used for suggestions only.
 */
public class OpenFoodFactsSource implements ProductDataSource {

    static final String SOURCE = "openfoodfacts";
    private static final String USER_AGENT = "DAHAEM-Household/1.0";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public OpenFoodFactsSource(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Optional<ProductSuggestion> find(String code) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v2/product/" + code
                        + ".json?fields=product_name,brands,quantity,product_quantity,product_quantity_unit"))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new IOException("Open Food Facts answered " + response.statusCode());
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while asking Open Food Facts", e);
        }
    }

    /** Reads an Open Food Facts product answer; empty when it says the product is unknown or has no name. */
    static Optional<ProductSuggestion> parse(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        if (root.path("status").asInt(0) != 1) {
            return Optional.empty();
        }
        JsonNode product = root.path("product");
        String name = text(product, "product_name");
        if (name == null) {
            return Optional.empty();
        }
        String brands = text(product, "brands");
        String brand = brands == null ? null : brands.split(",")[0].trim();
        String quantity = text(product, "quantity");
        StorePromotionService.Pack pack = quantity == null ? null : StorePromotionService.parseSize(quantity);
        if (pack == null && product.hasNonNull("product_quantity") && text(product, "product_quantity_unit") != null) {
            pack = StorePromotionService.parseSize(product.path("product_quantity").asText() + " "
                    + text(product, "product_quantity_unit"));
        }
        return Optional.of(new ProductSuggestion(name, brand == null || brand.isEmpty() ? null : brand,
                pack == null ? null : pack.size(), pack == null ? null : pack.unit(), quantity, SOURCE));
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
