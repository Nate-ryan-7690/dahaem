package de.dahaem.stores;

/**
 * One difference found by the range comparator between the last pull and this one.
 * Stored in store_catalog_change so the app can later show "what changed at ALDI".
 */
public record CatalogChange(String externalId, String name, Type type, Double oldPrice, Double newPrice) {

    public enum Type {
        NEW,        // product appeared in the range
        REMOVED,    // product no longer listed
        RETURNED,   // product was removed earlier and is listed again
        PRICE       // normal price changed
    }
}
