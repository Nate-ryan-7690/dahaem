package de.dahaem.tags;

import java.util.List;
import java.util.Set;

/**
 * What the tagger needs to know about one recipe. Nutrition is per serving, times are minutes;
 * {@code cuisine} is null when the recipe has none (or the column does not exist yet).
 * {@code unmatched} are the recipe's rows in recipe_unmatched_ingredient (lines the importer could not match,
 * or one 'unchecked' row): any of them makes the recipe "unklar".
 */
public record RecipeFacts(int id, String name, int prepMinutes, int cookMinutes, Integer calories, Integer protein,
                          String cuisine, List<Ingredient> ingredients, List<Unmatched> unmatched) {

    public RecipeFacts(int id, String name, int prepMinutes, int cookMinutes, Integer calories, Integer protein,
                       String cuisine, List<Ingredient> ingredients) {
        this(id, name, prepMinutes, cookMinutes, calories, protein, cuisine, ingredients, List.of());
    }

    /**
     * One ingredient line. {@code item} is null when the line has no catalogue item, which makes the recipe's diet
     * unknown. {@code animals} are the CERTAIN animals stored for the item (they give Tier tags),
     * {@code possibleAnimals} the ones a generic item may come from (no Tier tag; they block vegetarisch).
     */
    public record Ingredient(CatalogItem item, Set<String> animals, Set<String> possibleAnimals) {

        public Ingredient(CatalogItem item, Set<String> animals) {
            this(item, animals, Set.of());
        }

        /** True if the item has any animal, certain or possible. */
        public boolean hasAnyAnimal() {
            return !animals.isEmpty() || !possibleAnimals.isEmpty();
        }
    }

    /** A row of recipe_unmatched_ingredient: {@code lineNo} is null for an 'unchecked' recipe. */
    public record Unmatched(Integer lineNo, String rawText, String reason) {

        public static final String UNMATCHED = "unmatched";
        public static final String UNCHECKED = "unchecked";
    }
}
