package de.dahaem.api;

/**
 * Body of PUT /api/barcodes/{code}: what the barcode is. {@code inventoryItemId} (the catalogue item), {@code packSize}
 * and {@code packUnit} (like a store item: 500 g, 1000 ml, 6 stück) are required; {@code brand} (blank or missing =
 * no brand), {@code name} (as printed) and {@code storeItemId} (also link the store item that carries it, which must be
 * for the same catalogue item) are optional.
 */
public record AssignBarcodeRequest(Integer inventoryItemId, String brand, Integer packSize, String packUnit,
                                   String name, Integer storeItemId) {
}
