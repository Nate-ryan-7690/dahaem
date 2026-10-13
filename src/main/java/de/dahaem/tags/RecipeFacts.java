package de.dahaem.tags;

import java.util.List;
import java.util.Set;

/**
 * What the tagger needs to know about one recipe. Nutrition is per serving, times are minutes;
 * {@code cuisine} is null when the recipe has none (or the column does not exist yet).
 */
public record RecipeFacts(int id, String name, int prepMinutes, int cookMinutes, Integer calories, Integer protein,
                          String cuisine, List<Ingredient> ingredients) {

    /**
     * One ingredient line. {@code item} is null when the line has no catalogue item (unmatched), which
     * makes the recipe's diet unknown. {@code animals} are the animals stored for the item.
     */
    public record Ingredient(CatalogItem item, Set<String> animals) {
    }
}
