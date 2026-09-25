package de.dahaem.stores;

/**
 * One flavour/size of a store product, e.g. "Natur, 150 g" of ALDI's "Ziegenkäse".
 *
 * A record is Java's built-in plain data holder: the compiler generates the
 * fields, constructor, getters (named like the fields: name(), size()),
 * equals, hashCode and toString.
 */
public record ProductVariant(String name, String size) {
}
