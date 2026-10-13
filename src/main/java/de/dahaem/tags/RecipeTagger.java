package de.dahaem.tags;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Works out the tags a recipe gets from DAHÄM's own data. Pure: no SQL, no I/O.
 *
 * <ul>
 *   <li><b>Tier</b>: every animal of every ingredient item.</li>
 *   <li><b>vegetarisch</b>: only when EVERY ingredient has a catalogue item and none of them is meat / fish
 *       or has an animal; <b>vegan</b>: vegetarisch and no dairy, egg, honey or other animal product
 *       ({@link AnimalProducts}). An ingredient with no item makes the diet unknown: neither tag, since
 *       unknown is not vegetarian. A recipe without any ingredient is unknown too.</li>
 *   <li><b>schnell</b>: prep + cook at most the configured minutes, only when both are above 0.</li>
 *   <li><b>kalorienarm / proteinreich</b>: per-serving nutrition against the configured thresholds.</li>
 *   <li><b>Küche</b>: the recipe's cuisine.</li>
 * </ul>
 */
public class RecipeTagger {

    /** The result for one recipe. {@code dietKnown} is false when vegetarisch / vegan could not be judged. */
    public record Derivation(Set<DerivedTag> tags, boolean dietKnown) {
    }

    private final TagSettings settings;

    public RecipeTagger(TagSettings settings) {
        this.settings = settings;
    }

    public Derivation derive(RecipeFacts recipe) {
        Set<DerivedTag> tags = new LinkedHashSet<>();

        Set<String> animals = new LinkedHashSet<>();
        for (RecipeFacts.Ingredient ingredient : recipe.ingredients()) {
            animals.addAll(ingredient.animals());
        }
        for (String animal : Animals.sorted(animals)) {
            tags.add(new DerivedTag(animal, DerivedTag.TIER));
        }

        boolean dietKnown = !recipe.ingredients().isEmpty()
                && recipe.ingredients().stream().allMatch(i -> i.item() != null);
        if (dietKnown) {
            boolean vegetarian = recipe.ingredients().stream().noneMatch(i ->
                    AnimalProducts.vegetarianBlocker(i.item(), !i.animals().isEmpty()).isPresent());
            if (vegetarian) {
                tags.add(new DerivedTag(DerivedTag.VEGETARISCH, DerivedTag.ERNAEHRUNG));
                boolean vegan = recipe.ingredients().stream().noneMatch(i ->
                        AnimalProducts.veganBlocker(i.item()).isPresent());
                if (vegan) {
                    tags.add(new DerivedTag(DerivedTag.VEGAN, DerivedTag.ERNAEHRUNG));
                }
            }
        }

        if (recipe.prepMinutes() > 0 && recipe.cookMinutes() > 0
                && recipe.prepMinutes() + recipe.cookMinutes() <= settings.quickMaxMinutes()) {
            tags.add(new DerivedTag(DerivedTag.SCHNELL, DerivedTag.ZEIT));
        }
        if (recipe.calories() != null && recipe.calories() > 0 && recipe.calories() <= settings.lowCalorieMaxKcal()) {
            tags.add(new DerivedTag(DerivedTag.KALORIENARM, DerivedTag.NAEHRWERTE));
        }
        if (recipe.protein() != null && recipe.protein() >= settings.highProteinMinGrams()) {
            tags.add(new DerivedTag(DerivedTag.PROTEINREICH, DerivedTag.NAEHRWERTE));
        }
        if (recipe.cuisine() != null && !recipe.cuisine().isBlank()) {
            tags.add(new DerivedTag(recipe.cuisine().trim(), DerivedTag.KUECHE));
        }
        return new Derivation(tags, dietKnown);
    }
}
