package de.dahaem.stores;

/** One inventory_item catalog row, with its normalized names cached for matching. */
public record CatalogItem(int id, String english, String deutsch, String normEnglish, String normDeutsch) {
}
