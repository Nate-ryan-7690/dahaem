package de.dahaem.diet;

import de.dahaem.tags.CatalogItem;
import de.dahaem.tags.RecipeFacts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The diet filter on made-up recipes: no database, the facts are built by hand. */
class RecipeDietFilterTest {

    private static final DietPresets PRESETS = DietPresets.load(DietPresets.DEFAULT_FILE);
    private static final Map<String, String> GROUPS = Map.of("Glutenhaltig", "Inhalt", "Lieblingsessen", "Eigene",
            "schnell", "Zeit");
    private static final RecipeDietFilter FILTER = new RecipeDietFilter(GROUPS::get);

    private static int nextId = 100;

    private static RecipeFacts.Ingredient plant(String name, String category) {
        return new RecipeFacts.Ingredient(new CatalogItem(nextId++, name, category, null), Set.of());
    }

    private static RecipeFacts.Ingredient meat(String name, String... animals) {
        return new RecipeFacts.Ingredient(new CatalogItem(nextId++, name, "meat", "fresh meat"), Set.of(animals));
    }

    private static RecipeFacts.Ingredient generic(String name, String category, String... possible) {
        return new RecipeFacts.Ingredient(new CatalogItem(nextId++, name, category, null), Set.of(), Set.of(possible));
    }

    private static RecipeFacts.Ingredient wine() {
        return new RecipeFacts.Ingredient(new CatalogItem(nextId++, "rotwein", "beverages", "wine", true), Set.of());
    }

    private static RecipeDietFacts recipe(Set<String> tags, RecipeFacts.Ingredient... ingredients) {
        return new RecipeDietFacts(1, tags, List.of(ingredients), List.of());
    }

    private static RecipeDietFacts withUnmatched(Set<String> tags, RecipeFacts.Ingredient... ingredients) {
        return new RecipeDietFacts(1, tags, List.of(ingredients),
                List.of(new RecipeFacts.Unmatched(4, "50 g Speck", "unmatched")));
    }

    private static DietRules rules(String... diets) {
        return DietRules.of(PRESETS, List.of(diets), null, null, null, null, false);
    }

    private static DietStatus status(DietRules rules, RecipeDietFacts recipe) {
        return FILTER.judge(rules, recipe).status();
    }

    // ---------- certain exclusions ----------

    @Test
    void aCertainAnimalTagIsHiddenUnderHalalAndKosher() {
        RecipeDietFacts pork = recipe(Set.of("Schwein"), meat("schweinefilet", "Schwein"), plant("karotten", "vegetable"));
        assertEquals(DietStatus.HIDDEN, status(rules("halal"), pork));
        assertEquals(DietStatus.HIDDEN, status(rules("koscher"), pork));
        RecipeDietFacts beef = recipe(Set.of("Rind"), meat("rindersteak", "Rind"), plant("karotten", "vegetable"));
        assertEquals(DietStatus.OK, status(rules("halal"), beef));
        assertEquals(DietStatus.OK, status(rules("koscher"), beef));
    }

    @Test
    void aStaleTagRunCanOnlyMakeTheAnswerSaferNotLessSafe() {
        // the item has Schwein recorded, the recipe's tags were derived before that
        RecipeDietFacts stale = recipe(Set.of(), meat("schweinefilet", "Schwein"));
        assertEquals(DietStatus.HIDDEN, status(rules("halal"), stale));
    }

    @Test
    void alcoholComesFromTheItemFlag() {
        RecipeDietFacts stew = recipe(Set.of("Alkohol"), meat("rindersteak", "Rind"), wine());
        assertEquals(DietStatus.HIDDEN, status(rules("halal"), stew));
        assertEquals(DietStatus.HIDDEN, status(rules("halal"), recipe(Set.of(), meat("rindersteak", "Rind"), wine())),
                "no stored tag, the flag on the item is enough");
        assertEquals(DietStatus.OK, status(rules("halal"), recipe(Set.of(), plant("karotten", "vegetable"))));
    }

    @Test
    void kosherHidesRabbitHorseSeafoodAndMeatWithMilk() {
        for (String animal : List.of("Kaninchen", "Pferd", "Meeresfrüchte")) {
            assertEquals(DietStatus.HIDDEN, status(rules("koscher"), recipe(Set.of(animal), meat("x", animal))), animal);
        }
        RecipeDietFacts chickenCheese = recipe(Set.of("Fleisch mit Milch", "Hähnchen"), meat("hähnchen", "Hähnchen"),
                plant("gouda", "dairy"));
        assertEquals(DietStatus.HIDDEN, status(rules("koscher"), chickenCheese));
        assertEquals(DietStatus.HIDDEN, status(rules("koscher"),
                recipe(Set.of("Hähnchen"), meat("hähnchen", "Hähnchen"), plant("gouda", "dairy"))),
                "meat with milk is also seen from the ingredients when the tag is missing");
        assertEquals(DietStatus.OK, status(rules("koscher"), recipe(Set.of("Fisch"), meat("lachs", "Fisch"), plant("sahne", "dairy"))));
        assertEquals(DietStatus.OK, status(rules("halal"), chickenCheese), "Fleisch mit Milch is no halal rule");
    }

    @Test
    void excludedItemsAndExplicitTagsAreHard() {
        RecipeFacts.Ingredient coriander = plant("koriander", "culinary herbs");
        RecipeDietFacts withCoriander = recipe(Set.of(), coriander, plant("karotten", "vegetable"));
        DietRules noCoriander = DietRules.of(PRESETS, null, null, List.of(coriander.item().id()), null, null, false);
        assertEquals(DietStatus.HIDDEN, status(noCoriander, withCoriander));
        assertEquals(DietStatus.OK, status(noCoriander, recipe(Set.of(), plant("karotten", "vegetable"))));

        DietRules noQuick = DietRules.of(PRESETS, null, List.of("schnell", "Lieblingsessen"), null, null, null, false);
        assertEquals(DietStatus.HIDDEN, status(noQuick, recipe(Set.of("schnell"), plant("karotten", "vegetable"))));
        assertEquals(DietStatus.OK, status(noQuick, recipe(Set.of(), plant("karotten", "vegetable"))));
    }

    // ---------- possible animals: anpassbar ----------

    @Test
    void aPossibleAnimalMakesTheRecipeAnpassbarWithAHintPerItem() {
        RecipeFacts.Ingredient salami = generic("salami", "meat", "Schwein", "Rind", "Geflügel");
        RecipeFacts.Ingredient gelatine = generic("gelatine", "baking supplies", "Schwein", "Rind");
        DietVerdict v = FILTER.judge(rules("halal"), recipe(Set.of(), salami, gelatine, plant("karotten", "vegetable")));

        assertEquals(DietStatus.ANPASSBAR, v.status());
        assertEquals(2, v.hints().size());
        DietVerdict.Hint salamiHint = v.hints().get(0);
        assertEquals(salami.item().id(), salamiHint.itemId());
        assertEquals("salami", salamiHint.name());
        assertEquals(List.of("Geflügel", "Rind"), salamiHint.allowedAnimals(), "Salami → Rind, Geflügel");
        assertEquals(List.of("Geflügel", "Rind", "Schwein"), salamiHint.possibleAnimals());
        assertEquals(List.of("Rind"), v.hints().get(1).allowedAnimals(), "Gelatine → Rind");
    }

    @Test
    void anItemWhoseEveryPossibleAnimalIsExcludedCannotBeSavedSoTheRecipeIsHidden() {
        RecipeFacts.Ingredient lard = generic("schmalz", "oils & vinegars", "Schwein");
        assertEquals(DietStatus.HIDDEN, status(rules("halal"), recipe(Set.of(), lard)));
        RecipeFacts.Ingredient gelatine = generic("gelatine", "baking supplies", "Schwein", "Rind");
        DietRules noPorkNoBeef = DietRules.of(PRESETS, List.of("halal"), List.of("Rind"), null, null, null, false);
        assertEquals(DietStatus.HIDDEN, status(noPorkNoBeef, recipe(Set.of(), gelatine)));
    }

    @Test
    void aPossibleAnimalThatIsNotExcludedIsNoProblem() {
        RecipeFacts.Ingredient salami = generic("salami", "meat", "Rind", "Geflügel");
        DietVerdict v = FILTER.judge(rules("halal"), recipe(Set.of(), salami));
        assertEquals(DietStatus.OK, v.status());
        assertTrue(v.hints().isEmpty());
    }

    @Test
    void kosherAndHalalHintsDifferByTheirExcludedAnimals() {
        RecipeFacts.Ingredient merguez = generic("merguez", "meat", "Rind", "Lamm");
        assertEquals(DietStatus.OK, status(rules("halal"), recipe(Set.of(), merguez)));
        RecipeFacts.Ingredient wiener = generic("wiener", "meat", "Schwein", "Rind", "Geflügel");
        assertEquals(List.of("Geflügel", "Rind"), FILTER.judge(rules("koscher"), recipe(Set.of(), wiener)).hints().get(0).allowedAnimals());
    }

    // ---------- required tags: vegetarisch / vegan ----------

    @Test
    void vegetarischNeedsTheTagAndAKnownRecipe() {
        RecipeFacts.Ingredient carrot = plant("karotten", "vegetable");
        assertEquals(DietStatus.OK, status(rules("vegetarisch"), recipe(Set.of("vegetarisch", "vegan"), carrot)));
        assertEquals(DietStatus.HIDDEN, status(rules("vegetarisch"), recipe(Set.of(), carrot)),
                "the tag was not derived (yet): not shown as vegetarian");
        assertEquals(DietStatus.HIDDEN, status(rules("vegetarisch"),
                recipe(Set.of("Hähnchen"), meat("hähnchen", "Hähnchen"), carrot)));
    }

    @Test
    void aRecipeWithAnUnmatchedLineIsUnclearNotVegetarianAndNotHidden() {
        RecipeFacts.Ingredient carrot = plant("karotten", "vegetable");
        assertEquals(DietStatus.UNCLEAR, status(rules("vegetarisch"), withUnmatched(Set.of("unklar"), carrot)));
        assertEquals(DietStatus.UNCLEAR, status(rules("vegan"), withUnmatched(Set.of("unklar"), carrot)));
        assertEquals(DietStatus.UNCLEAR, status(rules("vegetarisch"), withUnmatched(Set.of("vegetarisch"), carrot)),
                "a stale vegetarisch tag does not make an unmatched line known");
        assertEquals(DietStatus.HIDDEN, status(rules("vegetarisch"),
                withUnmatched(Set.of("Hähnchen", "unklar"), meat("hähnchen", "Hähnchen"), carrot)),
                "what we do know already rules it out: unmatched lines cannot change that");
        assertEquals(DietStatus.HIDDEN, status(rules("vegan"), withUnmatched(Set.of("unklar"), plant("butter", "dairy"))),
                "butter is known and not vegan");
        assertEquals(DietStatus.UNCLEAR, status(rules("vegetarisch"), withUnmatched(Set.of("unklar"), plant("butter", "dairy"))),
                "butter is vegetarian, the unmatched line could still be Speck");
    }

    @Test
    void aPossibleAnimalBlocksVegetarianCompletely() {
        RecipeFacts.Ingredient gelatine = generic("gelatine", "baking supplies", "Schwein", "Rind");
        assertEquals(DietStatus.HIDDEN, status(rules("vegetarisch"), recipe(Set.of("vegetarisch"), gelatine)),
                "even with a tag: the item may be an animal product");
    }

    @Test
    void veganNeedsMoreThanVegetarian() {
        RecipeFacts.Ingredient butter = plant("butter", "dairy");
        assertEquals(DietStatus.OK, status(rules("vegetarisch"), recipe(Set.of("vegetarisch"), butter)));
        assertEquals(DietStatus.HIDDEN, status(rules("vegan"), recipe(Set.of("vegetarisch"), butter)));
        assertEquals(DietStatus.OK, status(rules("vegan"), recipe(Set.of("vegetarisch", "vegan"), plant("hafermilch", "dairy"))));
    }

    // ---------- unclear ----------

    @Test
    void anUnmatchedLineMakesAHardIngredientRuleUnclearUnlessSomethingCertainHidesTheRecipe() {
        RecipeFacts.Ingredient carrot = plant("karotten", "vegetable");
        assertEquals(DietStatus.UNCLEAR, status(rules("halal"), withUnmatched(Set.of("unklar"), carrot)));
        assertEquals(DietStatus.HIDDEN, status(rules("halal"),
                withUnmatched(Set.of("Schwein", "unklar"), meat("schweinefilet", "Schwein"), carrot)));
        assertEquals(DietStatus.OK, status(rules("halal"), recipe(Set.of(), carrot)));
        RecipeFacts.Ingredient salami = generic("salami", "meat", "Schwein", "Rind");
        DietVerdict both = FILTER.judge(rules("halal"), withUnmatched(Set.of("unklar"), salami));
        assertEquals(DietStatus.UNCLEAR, both.status(), "unclear is the safer answer");
        assertEquals(1, both.hints().size(), "and the hints are still there");
    }

    @Test
    void aRuleThatDoesNotDependOnTheIngredientsIgnoresUnmatchedLines() {
        DietRules noQuick = DietRules.of(PRESETS, null, List.of("schnell"), null, null, null, false);
        assertEquals(DietStatus.OK, status(noQuick, withUnmatched(Set.of(), plant("karotten", "vegetable"))));
        DietRules noGluten = DietRules.of(PRESETS, null, List.of("Glutenhaltig"), null, null, null, false);
        assertEquals(DietStatus.UNCLEAR, status(noGluten, withUnmatched(Set.of(), plant("karotten", "vegetable"))),
                "a tag in the group Inhalt is derived from the ingredients");
    }

    @Test
    void aRecipeWithoutIngredientsIsUnclearUnderAnIngredientRule() {
        assertEquals(DietStatus.UNCLEAR, status(rules("halal"), recipe(Set.of())));
        assertEquals(DietStatus.OK, status(DietRules.none(), recipe(Set.of())));
    }

    @Test
    void anExcludedItemIsUnclearWhenALineIsUnmatched() {
        DietRules noCoriander = DietRules.of(PRESETS, null, null, List.of(9999), null, null, false);
        assertEquals(DietStatus.UNCLEAR, status(noCoriander, withUnmatched(Set.of(), plant("karotten", "vegetable"))));
    }

    // ---------- soft rules ----------

    @Test
    void softRulesNeverChangeTheStatusButListWhatMatched() {
        RecipeFacts.Ingredient mushrooms = plant("champignons", "vegetable");
        RecipeDietFacts r = recipe(Set.of("Schwein"), meat("schweinefilet", "Schwein"), mushrooms);
        DietRules soft = DietRules.of(PRESETS, null, null, null, List.of("Schwein", "Rind"),
                List.of(mushrooms.item().id(), 99999), false);

        DietVerdict v = FILTER.judge(soft, r);

        assertEquals(DietStatus.OK, v.status());
        assertTrue(v.hasSoftHits());
        assertEquals(List.of("Schwein"), v.soft().tags());
        assertEquals(List.of(new DietVerdict.Item(mushrooms.item().id(), "champignons")), v.soft().items());
    }

    @Test
    void aSoftHitThroughAPossibleAnimalIsListedAsPossible() {
        RecipeFacts.Ingredient salami = generic("salami", "meat", "Schwein", "Rind");
        DietVerdict v = FILTER.judge(DietRules.of(PRESETS, null, null, null, List.of("Schwein"), null, false),
                recipe(Set.of(), salami));
        assertEquals(DietStatus.OK, v.status());
        assertEquals(1, v.soft().possibleItems().size());
        assertEquals(List.of("Rind"), v.soft().possibleItems().get(0).allowedAnimals());
        assertTrue(v.soft().tags().isEmpty());
    }

    @Test
    void noSoftHitMeansNoSoftObject() {
        assertNull(FILTER.judge(DietRules.of(PRESETS, null, null, null, List.of("Schwein"), null, false),
                recipe(Set.of(), plant("karotten", "vegetable"))).soft());
        assertNull(FILTER.judge(rules("halal"), recipe(Set.of(), plant("karotten", "vegetable"))).soft());
    }

    @Test
    void softHitsSortAfterTheOthersAndStatusOrdersWithinAGroup() {
        DietVerdict ok = new DietVerdict(DietStatus.OK, List.of(), null);
        DietVerdict anpassbar = new DietVerdict(DietStatus.ANPASSBAR, List.of(), null);
        DietVerdict unclear = new DietVerdict(DietStatus.UNCLEAR, List.of(), null);
        DietVerdict.SoftHits hits = new DietVerdict.SoftHits(List.of("Rind"), List.of(), List.of());
        DietVerdict okSoft = new DietVerdict(DietStatus.OK, List.of(), hits);
        DietVerdict unclearSoft = new DietVerdict(DietStatus.UNCLEAR, List.of(), hits);

        List<DietVerdict> sorted = new ArrayList<>(List.of(unclearSoft, unclear, okSoft, anpassbar, ok));
        sorted.sort(RecipeDietFilter.displayOrder());
        assertEquals(List.of(ok, anpassbar, unclear, okSoft, unclearSoft), sorted);
        Comparator<DietVerdict> order = RecipeDietFilter.displayOrder();
        assertEquals(0, order.compare(ok, new DietVerdict(DietStatus.OK, List.of(), null)), "stable within a group");
    }

    @Test
    void noRulesMeansEveryRecipeIsOk() {
        assertEquals(DietStatus.OK, status(DietRules.none(), withUnmatched(Set.of(), meat("x", "Schwein"))));
    }
}
