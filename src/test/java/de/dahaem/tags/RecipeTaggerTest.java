package de.dahaem.tags;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tag rules, with no database. */
class RecipeTaggerTest {

    private static final RecipeTagger TAGGER = new RecipeTagger(TagSettings.defaults());

    private static RecipeFacts.Ingredient veg(String name, String category) {
        return new RecipeFacts.Ingredient(new CatalogItem(1, name, category, null), Set.of());
    }

    private static RecipeFacts.Ingredient meat(String name, String... animals) {
        return new RecipeFacts.Ingredient(new CatalogItem(2, name, "meat", "fresh meat"), Set.of(animals));
    }

    private static RecipeFacts recipe(int prep, int cook, Integer kcal, Integer protein, String cuisine,
                                      RecipeFacts.Ingredient... ingredients) {
        return new RecipeFacts(1, "Test", prep, cook, kcal, protein, cuisine, List.of(ingredients));
    }

    private static Set<String> names(RecipeTagger.Derivation d) {
        return d.tags().stream().map(DerivedTag::name).collect(Collectors.toSet());
    }

    @Test
    void everyAnimalOfEveryIngredientBecomesATierTag() {
        RecipeTagger.Derivation d = TAGGER.derive(recipe(10, 20, null, null, null,
                meat("gemischtes hackfleisch", "Rind", "Schwein"), meat("hähnchenbrustfilet", "Hähnchen"),
                veg("zwiebeln", "vegetable")));
        assertEquals(Set.of("Rind", "Schwein", "Hähnchen", "schnell"), names(d));
        assertTrue(d.tags().contains(new DerivedTag("Rind", "Tier")));
        assertFalse(names(d).contains("vegetarisch"));
    }

    @Test
    void allVegetableIsVegetarianAndVeganUnlessAnAnimalProductIsIn() {
        RecipeTagger.Derivation vegan = TAGGER.derive(recipe(0, 0, null, null, null,
                veg("karroten", "vegetable"), veg("hafermilch", "dairy"), veg("salz", "spices & seasonings")));
        assertEquals(Set.of("vegetarisch", "vegan"), names(vegan));
        assertTrue(vegan.tags().contains(new DerivedTag("vegan", "Ernährung")));

        RecipeTagger.Derivation vegetarian = TAGGER.derive(recipe(0, 0, null, null, null,
                veg("karroten", "vegetable"), veg("butter", "dairy"), veg("eier", "dairy")));
        assertEquals(Set.of("vegetarisch"), names(vegetarian));

        RecipeTagger.Derivation honey = TAGGER.derive(recipe(0, 0, null, null, null,
                veg("honig", "baking supplies")));
        assertEquals(Set.of("vegetarisch"), names(honey));
    }

    @Test
    void meatOrFishAnywhereMeansNeitherDietTag() {
        RecipeTagger.Derivation d = TAGGER.derive(recipe(0, 0, null, null, null,
                veg("karroten", "vegetable"), new RecipeFacts.Ingredient(
                        new CatalogItem(3, "Guanciale", "meat & poultry", null), Set.of())));
        assertTrue(names(d).isEmpty(), "meat without a recorded animal still blocks vegetarian: " + names(d));
        assertTrue(d.dietKnown());
    }

    @Test
    void anIngredientWithoutAnItemMeansTheDietIsUnknown() {
        RecipeTagger.Derivation d = TAGGER.derive(recipe(0, 0, null, null, null,
                veg("karroten", "vegetable"), new RecipeFacts.Ingredient(null, Set.of())));
        assertFalse(d.dietKnown());
        assertTrue(names(d).isEmpty(), "unknown is not vegetarian: " + names(d));

        RecipeTagger.Derivation noIngredients = TAGGER.derive(recipe(0, 0, null, null, null));
        assertFalse(noIngredients.dietKnown());
        assertTrue(names(noIngredients).isEmpty());
    }

    @Test
    void anUnknownIngredientStillGivesTheAnimalTagsOfTheKnownOnes() {
        RecipeTagger.Derivation d = TAGGER.derive(recipe(0, 0, null, null, null,
                meat("rindersteak", "Rind"), new RecipeFacts.Ingredient(null, Set.of())));
        assertEquals(Set.of("Rind"), names(d));
    }

    @Test
    void schnellNeedsBothTimesAndThirtyMinutesAtMost() {
        assertTrue(names(TAGGER.derive(recipe(10, 20, null, null, null))).contains("schnell"));
        assertFalse(names(TAGGER.derive(recipe(10, 21, null, null, null))).contains("schnell"));
        assertFalse(names(TAGGER.derive(recipe(0, 20, null, null, null))).contains("schnell"));
        assertFalse(names(TAGGER.derive(recipe(10, 0, null, null, null))).contains("schnell"));
        assertTrue(TAGGER.derive(recipe(5, 5, null, null, null)).tags().contains(new DerivedTag("schnell", "Zeit")));
    }

    @Test
    void nutritionThresholdsArePerServingAndUnknownGivesNoTag() {
        RecipeTagger.Derivation light = TAGGER.derive(recipe(0, 0, 600, 29, null));
        assertEquals(Set.of("kalorienarm"), names(light));
        assertTrue(light.tags().contains(new DerivedTag("kalorienarm", "Nährwerte")));

        assertEquals(Set.of("proteinreich"), names(TAGGER.derive(recipe(0, 0, 601, 30, null))));
        assertEquals(Set.of(), names(TAGGER.derive(recipe(0, 0, null, null, null))));
        assertEquals(Set.of(), names(TAGGER.derive(recipe(0, 0, 0, 0, null))), "0 kcal is a missing value");
    }

    @Test
    void thresholdsComeFromTheSettings() {
        RecipeTagger strict = new RecipeTagger(new TagSettings(15, 400, 40));
        assertEquals(Set.of(), names(strict.derive(recipe(10, 10, 500, 35, null))));
        assertEquals(Set.of("schnell", "kalorienarm", "proteinreich"),
                names(strict.derive(recipe(5, 10, 400, 40, null))));
    }

    @Test
    void cuisineBecomesAKuecheTag() {
        RecipeTagger.Derivation d = TAGGER.derive(recipe(0, 0, null, null, " Italienisch "));
        assertTrue(d.tags().contains(new DerivedTag("Italienisch", "Küche")));
        assertEquals(Set.of(), names(TAGGER.derive(recipe(0, 0, null, null, "  "))));
    }
}
