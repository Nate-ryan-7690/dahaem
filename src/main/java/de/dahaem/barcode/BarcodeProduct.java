package de.dahaem.barcode;

import java.util.List;

/**
 * What DAHÄM knows about a barcode: the catalogue item it is, the brand (null = none), the pack (size in unit, like a
 * store item) and the name as printed (may be null), plus the store items that carry it (one per store that sells it).
 */
public record BarcodeProduct(String code, int inventoryItemId, String nameDeutsch, String nameEnglish, String brand,
                             int packSize, String packUnit, String name, List<LinkedStoreItem> storeItems) {

    public BarcodeProduct {
        storeItems = List.copyOf(storeItems);
    }

    /** A store item that carries the barcode: which store, and its current normal price (null if unknown). */
    public record LinkedStoreItem(int storeItemId, String storeName, Double price) {
    }
}
