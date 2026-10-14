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
        assertEquals(Set.of("unklar"), names(d), "unknown is not vegetarian: " + names(d));
        assertTrue(d.tags().contains(new DerivedTag("unklar", "Ernährung")));

        RecipeTagger.Derivation noIngredients = TAGGER.derive(recipe(0, 0, null, null, null));
        assertFalse(noIngredients.dietKnown());
        assertEquals(Set.of("unklar"), names(noIngredients));
    }

    @Test
    void anUnknownIngredientStillGivesTheAnimalTagsOfTheKnownOnes() {
        RecipeTagger.Derivation d = TAGGER.derive(recipe(0, 0, null, null, null,
                meat("rindersteak", "Rind"), new RecipeFacts.Ingredient(null, Set.of())));
        assertEquals(Set.of("Rind", "unklar"), names(d));
    }

    @Test
    void schnellNeedsAKnownTotalOfThirtyMinutesAtMost() {
        assertTrue(names(TAGGER.derive(recipe(10, 20, null, null, null))).contains("schnell"));
        assertFalse(names(TAGGER.derive(recipe(10, 21, null, null, null))).contains("schnell"));
        // imported recipes: HelloFresh's total time is stored as cook time, prep is 0
        assertTrue(names(TAGGER.derive(recipe(0, 30, null, null, null))).contains("schnell"));
        assertFalse(names(TAGGER.derive(recipe(0, 35, null, null, null))).contains("schnell"));
        assertFalse(names(TAGGER.derive(recipe(0, 0, null, null, null))).contains("schnell"));
        assertTrue(TAGGER.derive(recipe(5, 5, null, null, null)).tags().contains(new DerivedTag("schnell", "Zeit")));
    }

    @Test
    void nutritionThresholdsArePerServingAndUnknownGivesNoTag() {
        RecipeTagger.Derivation light = TAGGER.derive(recipe(0, 0, 600, 29, null));
        assertEquals(Set.of("kalorienarm", "unklar"), names(light));
        assertTrue(light.tags().contains(new DerivedTag("kalorienarm", "Nährwerte")));

        assertEquals(Set.of("proteinreich", "unklar"), names(TAGGER.derive(recipe(0, 0, 601, 31, null))));
        assertEquals(Set.of("unklar"), names(TAGGER.derive(recipe(0, 0, null, null, null))));
        assertEquals(Set.of("unklar"), names(TAGGER.derive(recipe(0, 0, 0, 0, null))), "0 kcal is a missing value");
    }

    @Test
    void thresholdsComeFromTheSettings() {
        RecipeTagger strict = new RecipeTagger(new TagSettings(15, 400, 0.30, 40));
        assertEquals(Set.of("unklar"), names(strict.derive(recipe(10, 10, 500, 35, null))));
        assertEquals(Set.of("proteinreich", "unklar"), names(strict.derive(recipe(10, 10, 500, 40, null))), "32 % >= 30 %");
        assertEquals(Set.of("unklar"), names(strict.derive(recipe(10, 10, 600, 40, null))), "26.7 % < 30 %");
        assertEquals(Set.of("schnell", "kalorienarm", "proteinreich", "unklar"),
                names(strict.derive(recipe(5, 10, 400, 40, null))));
    }

    /** EU "high protein" (Regulation (EC) No 1924/2006): >= 20 % of the energy from protein (4 kcal/g), plus >= 20 g. */
    @Test
    void proteinreichNeedsTwentyPercentOfTheEnergyAndTwentyGrams() {
        assertTrue(proteinreich(400, 20), "exactly 20 g and exactly 20 % of 400 kcal");
        assertFalse(proteinreich(401, 20), "19.95 % of the energy");
        assertTrue(proteinreich(399, 20));
        assertFalse(proteinreich(300, 19), "25 % of the energy, but under 20 g");
        assertTrue(proteinreich(300, 20), "26.7 %");
        assertFalse(proteinreich(700, 34), "34 g, but 19.4 % of the energy");
        assertTrue(proteinreich(700, 35), "35 g = exactly 20 % of 700 kcal");
        assertTrue(proteinreich(1000, 50));
        assertFalse(proteinreich(1000, 49));
        assertFalse(proteinreich(null, 40), "no kcal: no tag");
        assertFalse(proteinreich(0, 40), "0 kcal is a missing value");
        assertFalse(TAGGER.derive(recipe(0, 0, 400, null, null)).tags().contains(
                new DerivedTag("proteinreich", "Nährwerte")));
    }

    private static boolean proteinreich(Integer kcal, int protein) {
        return TAGGER.derive(recipe(0, 0, kcal, protein, null)).tags()
                .contains(new DerivedTag("proteinreich", "Nährwerte"));
    }

    @Test
    void settingsReadTheEnergyShareAndGramsFromTheProperties() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("tags", ".properties");
        file.toFile().deleteOnExit();
        java.nio.file.Files.writeString(file, "high-protein.energy.share=0.25\nhigh-protein.min.grams=25\n");
        TagSettings s = TagSettings.load(file);
        assertEquals(0.25, s.highProteinEnergyShare());
        assertEquals(25, s.highProteinMinGrams());
        assertEquals(30, s.quickMaxMinutes(), "missing key: default");
        assertEquals(0.20, TagSettings.defaults().highProteinEnergyShare());
        assertEquals(20, TagSettings.defaults().highProteinMinGrams());

        java.nio.file.Files.writeString(file, "high-protein.energy.share=20\n");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> TagSettings.load(file));
    }

    @Test
    void cuisineBecomesAKuecheTag() {
        RecipeTagger.Derivation d = TAGGER.derive(recipe(0, 0, null, null, " Italienisch "));
        assertTrue(d.tags().contains(new DerivedTag("Italienisch", "Küche")));
        assertEquals(Set.of("unklar"), names(TAGGER.derive(recipe(0, 0, null, null, "  "))));
    }

    // ---------- diet filter additions (migration 0012) ----------

    private static RecipeFacts.Ingredient generic(String name, String category, String... possible) {
        return new RecipeFacts.Ingredient(new CatalogItem(4, name, category, null), Set.of(), Set.of(possible));
    }

    private static RecipeFacts withUnmatched(RecipeFacts.Unmatched unmatched, RecipeFacts.Ingredient... ingredients) {
        return new RecipeFacts(1, "Test", 0, 0, null, null, null, List.of(ingredients), List.of(unmatched));
    }

    @Test
    void anUnmatchedLineMakesTheRecipeUnklarAndNeverVegetarianOrVegan() {
        RecipeFacts.Ingredient carrot = veg("karotten", "vegetable");
        RecipeTagger.Derivation d = TAGGER.derive(withUnmatched(
                new RecipeFacts.Unmatched(3, "50 g Speck", "unmatched"), carrot));
        assertFalse(d.dietKnown());
        assertEquals(Set.of("unklar"), names(d));

        RecipeTagger.Derivation unchecked = TAGGER.derive(withUnmatched(
                new RecipeFacts.Unmatched(null, "(keine Quell-URL)", "unchecked"), carrot));
        assertEquals(Set.of("unklar"), names(unchecked));

        RecipeTagger.Derivation clean = TAGGER.derive(recipe(0, 0, null, null, null, carrot));
        assertEquals(Set.of("vegetarisch", "vegan"), names(clean));
    }

    @Test
    void aPossibleAnimalGivesNoTierTagButBlocksVegetarisch() {
        RecipeTagger.Derivation d = TAGGER.derive(recipe(0, 0, null, null, null,
                generic("salami", "meat", "Schwein", "Rind", "Geflügel"), veg("karotten", "vegetable")));
        assertEquals(Set.of(), names(d), "no Tier tag, no vegetarisch");
        assertTrue(d.dietKnown());

        RecipeTagger.Derivation jelly = TAGGER.derive(recipe(0, 0, null, null, null,
                generic("gelatine", "baking supplies", "Schwein", "Rind"), veg("karotten", "vegetable")));
        assertEquals(Set.of(), names(jelly), "gelatine is not meat, but its possible animals block vegetarisch");
    }

    @Test
    void tierTagsComeFromCertainAnimalsOnly() {
        RecipeFacts.Ingredient mixed = new RecipeFacts.Ingredient(new CatalogItem(5, "chorizo", "meat", null),
                Set.of("Schwein"), Set.of("Rind"));
        assertEquals(Set.of("Schwein"), names(TAGGER.derive(recipe(0, 0, null, null, null, mixed))));
    }

    @Test
    void alcoholComesFromTheItemFlag() {
        RecipeFacts.Ingredient wine = new RecipeFacts.Ingredient(
                new CatalogItem(6, "rotwein", "beverages", "wine", true), Set.of());
        RecipeTagger.Derivation d = TAGGER.derive(recipe(0, 0, null, null, null, wine, veg("karotten", "vegetable")));
        assertTrue(d.tags().contains(new DerivedTag("Alkohol", "Inhalt")));
        assertTrue(names(d).contains("vegetarisch"), "wine alone is not an animal product here");
        assertFalse(names(TAGGER.derive(recipe(0, 0, null, null, null, veg("karotten", "vegetable"))))
                .contains("Alkohol"));
    }

    @Test
    void meatWithMilkNeedsALandAnimalCertainOrPossibleAndAMilkProduct() {
        RecipeFacts.Ingredient cheese = veg("gouda", "dairy");
        RecipeFacts.Ingredient cream = veg("sahne", "dairy");
        RecipeFacts.Ingredient egg = veg("eier", "dairy");
        RecipeTagger.Derivation chickenCheese = TAGGER.derive(recipe(0, 0, null, null, null,
                meat("hähnchenbrust", "Hähnchen"), cheese));
        assertTrue(chickenCheese.tags().contains(new DerivedTag("Fleisch mit Milch", "Inhalt")));
        assertTrue(names(TAGGER.derive(recipe(0, 0, null, null, null,
                generic("salami", "meat", "Schwein", "Rind"), cream))).contains("Fleisch mit Milch"),
                "a possible land animal counts");
        assertFalse(names(TAGGER.derive(recipe(0, 0, null, null, null, meat("lachsfilet", "Fisch"), cream)))
                .contains("Fleisch mit Milch"), "fish is not meat for this");
        assertFalse(names(TAGGER.derive(recipe(0, 0, null, null, null, meat("garnelen", "Meeresfrüchte"), cheese)))
                .contains("Fleisch mit Milch"));
        assertFalse(names(TAGGER.derive(recipe(0, 0, null, null, null, meat("hähnchenbrust", "Hähnchen"), egg)))
                .contains("Fleisch mit Milch"), "egg is not milk");
        assertFalse(names(TAGGER.derive(recipe(0, 0, null, null, null, meat("hähnchenbrust", "Hähnchen"),
                veg("hafermilch", "dairy")))).contains("Fleisch mit Milch"), "plant drink");
        assertTrue(names(TAGGER.derive(recipe(0, 0, null, null, null, meat("hähnchenbrust", "Hähnchen"),
                veg("butter", "dairy")))).contains("Fleisch mit Milch"), "butter is milk");
    }

    @Test
    void aMeatProductWithCheeseInItsNameIsNotTheDairyPartner() {
        RecipeFacts.Ingredient leberkaese = new RecipeFacts.Ingredient(
                new CatalogItem(7, "leberkäse", "meat", "wurst & aufschnitt"), Set.of("Schwein"));
        assertFalse(names(TAGGER.derive(recipe(0, 0, null, null, null, leberkaese))).contains("Fleisch mit Milch"));
    }

    @Test
    void milkProductsAreRecognisedByCategoryAndName() {
        assertTrue(AnimalProducts.isMilkProduct(new CatalogItem(1, "gouda", "dairy", "cheese")));
        assertTrue(AnimalProducts.isMilkProduct(new CatalogItem(1, "Frischkäse", "chilled", null)));
        assertTrue(AnimalProducts.isMilkProduct(new CatalogItem(1, "butter", "dairy", null)));
        assertFalse(AnimalProducts.isMilkProduct(new CatalogItem(1, "eier", "dairy", null)));
        assertFalse(AnimalProducts.isMilkProduct(new CatalogItem(1, "hafermilch", "dairy", null)));
        assertFalse(AnimalProducts.isMilkProduct(new CatalogItem(1, "erdnussbutter", "pantry", null)));
        assertFalse(AnimalProducts.isMilkProduct(new CatalogItem(1, "vegane sahne", "plant-based", null)));
        assertFalse(AnimalProducts.isMilkProduct(new CatalogItem(1, "karotten", "vegetable", null)));
    }
}
