package de.dahaem.diet;

import de.dahaem.tags.CatalogItem;
import de.dahaem.tags.RecipeFacts;

import java.util.List;
import java.util.Set;

/**
 * What the filter needs to know about one recipe: its stored tag names, its ingredients with their certain and
 * possible animals, and its unmatched lines (recipe_unmatched_ingredient). No SQL.
 */
public record RecipeDietFacts(int id, Set<String> tags, List<RecipeFacts.Ingredient> ingredients,
                              List<RecipeFacts.Unmatched> unmatched) {
}
