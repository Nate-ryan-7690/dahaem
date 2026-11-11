package de.dahaem.api;

/**
 * Body of PUT /api/barcodes/{code}: what the barcode is. {@code inventoryItemId} (the catalogue item), {@code packSize}
 * and {@code packUnit} (500 g, 1 kg, 1,5 l, 6 stück) are required; the pack is stored in g, ml or stück like a store
 * item (1 kg becomes 1000 g, 1,5 l is sent as 1.5 l or 1500 ml) and must fit the item's unit (else 400 PACK_UNIT_MISMATCH); {@code brand} (blank or missing =
 * no brand), {@code name} (as printed) and {@code storeItemId} (also link the store item that carries it, which must be
 * for the same catalogue item) are optional.
 */
public record AssignBarcodeRequest(Integer inventoryItemId, String brand, Integer packSize, String packUnit,
                                   String name, Integer storeItemId) {
}
