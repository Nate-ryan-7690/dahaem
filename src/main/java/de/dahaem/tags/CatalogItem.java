package de.dahaem.tags;

/**
 * One inventory_item as the tag logic sees it. {@code name} is the German name, or the English one when
 * the item has no German name (items added later were entered in German only).
 */
public record CatalogItem(int id, String name, String category, String subcategory) {

    public CatalogItem {
        category = category == null ? "" : category;
        subcategory = subcategory == null ? "" : subcategory;
    }
}
