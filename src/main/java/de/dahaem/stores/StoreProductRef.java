package de.dahaem.stores;

/** Minimal store_product row for matching: id, name, category. */
public record StoreProductRef(int id, String name, String category) {
}
