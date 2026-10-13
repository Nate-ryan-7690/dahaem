package de.dahaem.tags;

/**
 * One inventory_item as the tag logic sees it. {@code name} is the German name, or the English one when
 * the item has no German name (items added later were entered in German only). {@code alcohol} is the item's
 * alcohol flag (migration 0012): it contains alcohol as an ingredient.
 */
public record CatalogItem(int id, String name, String category, String subcategory, boolean alcohol) {

    public CatalogItem {
        category = category == null ? "" : category;
        subcategory = subcategory == null ? "" : subcategory;
    }

    public CatalogItem(int id, String name, String category, String subcategory) {
        this(id, name, category, subcategory, false);
    }
}
