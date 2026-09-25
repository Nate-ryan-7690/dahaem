package de.dahaem.stores;

/**
 * What the database already knows about a store product. Loaded before a range
 * import so the new pull can be compared against it.
 */
public record KnownProduct(int id, String externalId, String name, Double regularPrice, boolean available) {
}
