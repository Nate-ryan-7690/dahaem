package de.dahaem.diet;

import de.dahaem.tags.TagFixtures;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The filter service on made-up recipes in a throwaway MariaDB (Testcontainers). */
class DietFilterServiceTest {

    private DietFilterService service;
    private int carrot;
    private int pork;
    private int salami;
    private int vegetables;
    private int porkRoast;
    private int salamiPizza;
    private int speckSoup;
    private int noIngredients;

    @BeforeEach
    void setUp() {
        TestDatabase.reset();
        service = new DietFilterService(new DietFilterRepository(), DietPresets.load(DietPresets.DEFAULT_FILE));
        carrot = TagFixtures.item("karotten", "vegetable", null);
        pork = TagFixtures.item("schweinefilet", "meat", "fresh meat");
        salami = TagFixtures.item("salami", "meat", "wurst & aufschnitt");
        TagFixtures.animal(pork, "Schwein");
        TagFixtures.animalWithCertainty(salami, "Schwein", "possible");
        TagFixtures.animalWithCertainty(salami, "Rind", "possible");
        TagFixtures.animalWithCertainty(salami, "Geflügel", "possible");

        vegetables = TagFixtures.recipe("Gemüsepfanne", 10, 10, 300, 5);
        TagFixtures.ingredient(vegetables, carrot);
        TagFixtures.tag(vegetables, "vegetarisch", "Ernährung", "derived");
        porkRoast = TagFixtures.recipe("Schweinebraten", 10, 90, 700, 40);
        TagFixtures.ingredient(porkRoast, pork);
        TagFixtures.tag(porkRoast, "Schwein", "Tier", "derived");
        salamiPizza = TagFixtures.recipe("Salamipizza", 10, 20, 800, 30);
        TagFixtures.ingredient(salamiPizza, salami);
        speckSoup = TagFixtures.recipe("Suppe mit Speck", 10, 20, 300, 8);
        TagFixtures.ingredient(speckSoup, carrot);
        TagFixtures.unmatched(speckSoup, 2, "50 g Speck", "unmatched");
        noIngredients = TagFixtures.recipe("Leer", 1, 1, null, null);
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    private Map<Integer, DietVerdict> judge(String... diets) {
        DietRules rules = service.rules(List.of(diets), null, null, null, null, false);
        return service.judgeAll(rules, List.of(vegetables, porkRoast, salamiPizza, speckSoup, noIngredients));
    }

    @Test
    void halalOverAllRecipesAtOnce() {
        Map<Integer, DietVerdict> v = judge("halal");
        assertEquals(DietStatus.OK, v.get(vegetables).status());
        assertEquals(DietStatus.HIDDEN, v.get(porkRoast).status());
        assertEquals(DietStatus.ANPASSBAR, v.get(salamiPizza).status());
        assertEquals(List.of("Geflügel", "Rind"), v.get(salamiPizza).hints().get(0).allowedAnimals());
        assertEquals(DietStatus.UNCLEAR, v.get(speckSoup).status());
        assertEquals(DietStatus.UNCLEAR, v.get(noIngredients).status());
    }

    @Test
    void vegetarischOnlyKeepsTheTaggedKnownRecipe() {
        Map<Integer, DietVerdict> v = judge("vegetarisch");
        assertEquals(DietStatus.OK, v.get(vegetables).status());
        assertEquals(DietStatus.HIDDEN, v.get(porkRoast).status());
        assertEquals(DietStatus.HIDDEN, v.get(salamiPizza).status(), "a possible animal blocks vegetarisch");
        assertEquals(DietStatus.UNCLEAR, v.get(speckSoup).status(), "the dropped line could be anything");
    }

    @Test
    void oneRecipeCarriesItsUnmatchedLines() {
        DietFilterService.Judged judged = service.judge(
                service.rules(List.of("halal"), null, null, null, null, false), speckSoup);
        assertEquals(DietStatus.UNCLEAR, judged.verdict().status());
        assertEquals(1, judged.facts().unmatched().size());
        assertEquals("50 g Speck", judged.facts().unmatched().get(0).rawText());
        assertEquals(2, judged.facts().unmatched().get(0).lineNo());

        DietFilterService.Judged clean = service.judge(
                service.rules(List.of("halal"), null, null, null, null, false), vegetables);
        assertEquals(DietStatus.OK, clean.verdict().status());
        assertTrue(clean.facts().unmatched().isEmpty());
    }

    @Test
    void nothingIsWrittenAndTheFilterWorksWithoutAnyTagRun() {
        int before = TagFixtures.count("SELECT COUNT(*) FROM recipe_tag");
        judge("halal");
        assertEquals(before, TagFixtures.count("SELECT COUNT(*) FROM recipe_tag"));
        TagFixtures.run("DELETE FROM recipe_tag");
        assertEquals(DietStatus.HIDDEN, judge("halal").get(porkRoast).status(),
                "without stored tags the certain animal of the item still excludes it");
    }

    @Test
    void anUnknownPresetIsRejectedBeforeAnyWork() {
        assertThrows(IllegalArgumentException.class, () -> judge("paleo"));
    }

    @Test
    void itStopsWithAClearMessageWhenMigration0012IsMissing() {
        TagFixtures.run("DROP TABLE recipe_unmatched_ingredient");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> judge("halal"));
        assertTrue(e.getMessage().contains("0012_diet_filter.sql"));
    }
}
