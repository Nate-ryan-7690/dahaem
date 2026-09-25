package de.dahaem.stores;

/**
 * A matched store_product considered for promotion into the verified store_item layer.
 * Carries only what a store_item needs; completeness (parseable size, brand, price) is
 * decided in StorePromotionService.
 */
public record PromotableProduct(
        int storeProductId,
        int storeId,
        String brand,
        String name,
        String salesUnit,
        Double regularPrice,
        Double deposit,
        boolean available,
        int inventoryItemId) {
}
