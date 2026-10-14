package de.dahaem.tags;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Works out the tags a recipe gets from DAHÄM's own data. Pure: no SQL, no I/O.
 *
 * <ul>
 *   <li><b>Tier</b>: every CERTAIN animal of every ingredient item. A possible animal (Salami: Schwein, Rind,
 *       Geflügel) gives no Tier tag.</li>
 *   <li><b>vegetarisch</b>: only when EVERY ingredient has a catalogue item and none of them is meat / fish
 *       or has an animal, certain OR possible; <b>vegan</b>: vegetarisch and no dairy, egg, honey or other animal
 *       product ({@link AnimalProducts}). A recipe with a row in recipe_unmatched_ingredient (a line the importer
 *       could not match, or a recipe whose page was not checked) is <b>unklar</b> (Ernährung) and never
 *       vegetarisch / vegan, since unknown is not vegetarian. A recipe without any ingredient is unklar too.</li>
 *   <li><b>Alkohol</b> (Inhalt): an ingredient item with the alcohol flag. <b>Fleisch mit Milch</b> (Inhalt): an
 *       item with a land animal (certain or possible, not Fisch / Meeresfrüchte) together with a milk product.</li>
 *   <li><b>schnell</b>: prep + cook at most the configured minutes, only when the total is above 0
 *       (imported recipes store the total time as cook time, prep 0).</li>
 *   <li><b>kalorienarm</b>: at most the configured kcal per serving. <b>proteinreich</b>: at least 20 % of the energy
 *       from protein (EU "high protein", Regulation (EC) No 1924/2006) and at least 20 g protein per serving.</li>
 *   <li><b>Küche</b>: the recipe's cuisine.</li>
 * </ul>
 */
public class RecipeTagger {

    /** The result for one recipe. {@code dietKnown} is false when vegetarisch / vegan could not be judged (the recipe is "unklar"). */
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

        if (recipe.ingredients().stream().anyMatch(i -> i.item() != null && i.item().alcohol())) {
            tags.add(new DerivedTag(DerivedTag.ALKOHOL, DerivedTag.INHALT));
        }
        if (meatWithMilk(recipe)) {
            tags.add(new DerivedTag(DerivedTag.FLEISCH_MIT_MILCH, DerivedTag.INHALT));
        }

        boolean dietKnown = !recipe.ingredients().isEmpty() && recipe.unmatched().isEmpty()
                && recipe.ingredients().stream().allMatch(i -> i.item() != null);
        if (!dietKnown) {
            tags.add(new DerivedTag(DerivedTag.UNKLAR, DerivedTag.ERNAEHRUNG));
        } else {
            boolean vegetarian = recipe.ingredients().stream().noneMatch(i ->
                    AnimalProducts.vegetarianBlocker(i.item(), i.hasAnyAnimal()).isPresent());
            if (vegetarian) {
                tags.add(new DerivedTag(DerivedTag.VEGETARISCH, DerivedTag.ERNAEHRUNG));
                boolean vegan = recipe.ingredients().stream().noneMatch(i ->
                        AnimalProducts.veganBlocker(i.item()).isPresent());
                if (vegan) {
                    tags.add(new DerivedTag(DerivedTag.VEGAN, DerivedTag.ERNAEHRUNG));
                }
            }
        }

        int totalMinutes = recipe.prepMinutes() + recipe.cookMinutes();
        if (totalMinutes > 0 && totalMinutes <= settings.quickMaxMinutes()) {
            tags.add(new DerivedTag(DerivedTag.SCHNELL, DerivedTag.ZEIT));
        }
        if (recipe.calories() != null && recipe.calories() > 0 && recipe.calories() <= settings.lowCalorieMaxKcal()) {
            tags.add(new DerivedTag(DerivedTag.KALORIENARM, DerivedTag.NAEHRWERTE));
        }
        if (isHighProtein(recipe)) {
            tags.add(new DerivedTag(DerivedTag.PROTEINREICH, DerivedTag.NAEHRWERTE));
        }
        if (recipe.cuisine() != null && !recipe.cuisine().isBlank()) {
            tags.add(new DerivedTag(recipe.cuisine().trim(), DerivedTag.KUECHE));
        }
        return new Derivation(tags, dietKnown);
    }

    /**
     * "Proteinreich": at least the configured share of the energy from protein (EU "high protein", Regulation (EC)
     * No 1924/2006: 20 %, protein counted as 4 kcal per gram) AND at least the configured grams per serving.
     * Needs both values; 0 kcal is a missing value.
     */
    boolean isHighProtein(RecipeFacts recipe) {
        Integer protein = recipe.protein();
        Integer kcal = recipe.calories();
        if (protein == null || kcal == null || kcal <= 0 || protein < settings.highProteinMinGrams()) {
            return false;
        }
        // protein * 4 / kcal >= share, compared in whole numbers x 1000 of the share so 20 g in 400 kcal is exactly 20 %
        return Math.round(protein * 4.0 * 1000) >= Math.round(settings.highProteinEnergyShare() * 1000) * (long) kcal;
    }

    /** A land animal (certain or possible) in one item and a milk product in another. */
    static boolean meatWithMilk(RecipeFacts recipe) {
        boolean meat = false;
        boolean milk = false;
        for (RecipeFacts.Ingredient i : recipe.ingredients()) {
            if (i.animals().stream().anyMatch(Animals::isLandAnimal)
                    || i.possibleAnimals().stream().anyMatch(Animals::isLandAnimal)) {
                meat = true;
            } else if (i.item() != null && !i.hasAnyAnimal() && AnimalProducts.isMilkProduct(i.item())) {
                milk = true;
            }
        }
        return meat && milk;
    }
}
