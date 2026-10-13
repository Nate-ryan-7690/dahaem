package de.dahaem.tags;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static de.dahaem.tags.TagFixtures.count;
import static de.dahaem.tags.TagFixtures.tags;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The derived-tag run end to end on a made-up catalogue in a throwaway MariaDB (Testcontainers). */
class RecipeTagServiceTest {

    private RecipeTagRepository repository;
    private RecipeTagService service;
    private int mince;
    private int chicken;
    private int carrot;
    private int oat;
    private int butter;
    private int unclassifiedSausage;

    @BeforeEach
    void setUp() {
        TestDatabase.reset();
        repository = new RecipeTagRepository();
        service = new RecipeTagService(repository, TagSettings.defaults());
        mince = TagFixtures.item("gemischtes hackfleisch", "meat", "fresh meat");
        chicken = TagFixtures.item("hähnchenbrustfilet", "meat", "fresh meat");
        unclassifiedSausage = TagFixtures.item("salami", "meat", "wurst & aufschnitt");
        carrot = TagFixtures.item("karotten", "vegetable", null);
        oat = TagFixtures.item("hafermilch", "dairy", null);
        butter = TagFixtures.item("butter", "dairy", null);
        TagFixtures.animal(mince, "Rind");
        TagFixtures.animal(mince, "Schwein");
        TagFixtures.animal(chicken, "Hähnchen");
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    @Test
    void mixedMinceGivesRindAndSchwein() {
        int bolognese = TagFixtures.recipe("Bolognese", 15, 40, 750, 35);
        TagFixtures.ingredient(bolognese, mince);
        TagFixtures.ingredient(bolognese, carrot);

        service.commit(service.plan());

        assertEquals(List.of("proteinreich", "Rind", "Schwein"), tags(bolognese, "derived"));   // the collation sorts ignoring case
    }

    @Test
    void vegetarianVeganAndUnknownCases() {
        int vegan = TagFixtures.recipe("Möhrensuppe", 10, 20, 300, 8);
        TagFixtures.ingredient(vegan, carrot);
        TagFixtures.ingredient(vegan, oat);
        int vegetarian = TagFixtures.recipe("Möhren in Butter", 10, 20, 400, 8);
        TagFixtures.ingredient(vegetarian, carrot);
        TagFixtures.ingredient(vegetarian, butter);
        int meaty = TagFixtures.recipe("Hähnchen", 10, 20, 400, 8);
        TagFixtures.ingredient(meaty, chicken);
        TagFixtures.ingredient(meaty, carrot);
        int sausage = TagFixtures.recipe("Wurstsalat", 10, 20, 400, 8);   // salami: meat category, animal not recorded yet
        TagFixtures.ingredient(sausage, unclassifiedSausage);
        int empty = TagFixtures.recipe("Ohne Zutaten", 10, 20, 400, 8);

        RecipeTagService.Plan plan = service.commit(service.plan());

        assertEquals(List.of("kalorienarm", "schnell", "vegan", "vegetarisch"), tags(vegan, "derived"));
        assertEquals(List.of("kalorienarm", "schnell", "vegetarisch"), tags(vegetarian, "derived"));
        assertEquals(List.of("Hähnchen", "kalorienarm", "schnell"), tags(meaty, "derived"));
        assertEquals(List.of("kalorienarm", "schnell"), tags(sausage, "derived"),
                "meat with no recorded animal is not vegetarian, and gets no Tier tag");
        assertEquals(List.of("kalorienarm", "schnell"), tags(empty, "derived"),
                "no ingredients: diet unknown, so neither vegetarisch nor vegan");
        assertEquals(1, plan.recipesWithoutIngredients());
        assertEquals(0, plan.recipesWithUnlinkedIngredient(), "recipe_ingredient cannot hold a line without an item");
    }

    @Test
    void manualTagsSurviveARerunAndAreNeverDuplicatedOrTakenOver() {
        int recipe = TagFixtures.recipe("Gemüsepfanne", 10, 10, 300, 5);
        TagFixtures.ingredient(recipe, carrot);
        TagFixtures.tag(recipe, "Omas Rezept", "Eigene", "manual");
        TagFixtures.tag(recipe, "schnell", "Zeit", "manual");   // the tagger would derive it too

        service.commit(service.plan());
        service.commit(service.plan());

        assertEquals(List.of("Omas Rezept", "schnell"), tags(recipe, "manual"));
        assertEquals(List.of("kalorienarm", "vegan", "vegetarisch"), tags(recipe, "derived"),
                "schnell stays manual and is not added as derived");
    }

    @Test
    void rerunWithoutChangesChangesNothing() {
        int recipe = TagFixtures.recipe("Gemüsepfanne", 10, 10, 300, 5);
        TagFixtures.ingredient(recipe, carrot);

        RecipeTagService.Plan first = service.commit(service.plan());
        assertTrue(first.added() > 0);

        RecipeTagService.Plan second = service.plan();
        assertEquals(0, second.changes().size());
        assertEquals(0, second.added() + second.removed());
    }

    @Test
    void aDerivedTagThatNoLongerAppliesIsRemovedButAManualOneIsNot() {
        int recipe = TagFixtures.recipe("Gemüsepfanne", 10, 10, 300, 5);
        TagFixtures.ingredient(recipe, carrot);
        TagFixtures.tag(recipe, "Lieblingsessen", "Eigene", "manual");
        service.commit(service.plan());
        assertTrue(tags(recipe, "derived").contains("vegetarisch"));

        TagFixtures.ingredient(recipe, chicken);   // the recipe changes: now with chicken
        RecipeTagService.Plan plan = service.commit(service.plan());

        assertEquals(List.of("Hähnchen", "kalorienarm", "schnell"), tags(recipe, "derived"));
        assertEquals(List.of("Lieblingsessen"), tags(recipe, "manual"));
        assertEquals(2, plan.removed(), "vegan and vegetarisch go");
    }

    @Test
    void dryRunWritesNothingAndReportsCountsWithExamples() {
        for (int i = 1; i <= 12; i++) {
            int r = TagFixtures.recipe("Gemüse " + i, 10, 10, 300, 5);
            TagFixtures.ingredient(r, carrot);
        }
        RecipeTagService.Plan plan = service.plan();

        assertFalse(plan.committed());
        assertEquals(0, count("SELECT COUNT(*) FROM recipe_tag"));
        assertEquals(0, count("SELECT COUNT(*) FROM tag"), "a dry run does not even create the tags");
        assertEquals(12, plan.recipesPerTag().get("vegan"));
        assertEquals(10, plan.examplesPerTag().get("vegan").size(), "10 examples per tag");
        String text = plan.toText();
        assertTrue(text.contains("DRY RUN") && text.contains("vegan") && text.contains("Gemüse 1"), text);
    }

    @Test
    void tagsAreCreatedOnDemandInTheirGroup() {
        int recipe = TagFixtures.recipe("Hähnchen", 5, 5, 300, 40);
        TagFixtures.ingredient(recipe, chicken);
        service.commit(service.plan());

        assertEquals(1, count("SELECT COUNT(*) FROM tag WHERE tag_name = 'Hähnchen' AND tag_category = 'Tier'"));
        assertEquals(1, count("SELECT COUNT(*) FROM tag WHERE tag_name = 'schnell' AND tag_category = 'Zeit'"));
        assertEquals(1, count("SELECT COUNT(*) FROM tag WHERE tag_name = 'proteinreich' AND tag_category = 'Nährwerte'"));
        assertEquals(0, count("SELECT COUNT(*) FROM tag WHERE tag_name = 'vegan'"), "no recipe has it, so no tag row");
    }

    @Test
    void aFailureHalfWayRollsEverythingBack() {
        int recipe = TagFixtures.recipe("Gemüsepfanne", 10, 10, 300, 5);
        List<RecipeTagRepository.Change> changes = new ArrayList<>();
        changes.add(new RecipeTagRepository.Change(recipe, "vegan", "Ernährung", true));
        changes.add(new RecipeTagRepository.Change(recipe, "schnell", "Zeit", true));
        changes.add(new RecipeTagRepository.Change(999_999, "vegan", "Ernährung", true));   // no such recipe

        assertThrows(RuntimeException.class, () -> repository.apply(changes));

        assertEquals(0, count("SELECT COUNT(*) FROM recipe_tag"));
        assertEquals(0, count("SELECT COUNT(*) FROM tag"), "the tags created on the way were rolled back too");
    }

    @Test
    void cuisineIsANoOpUntilTheColumnExistsThenBecomesAKuecheTag() {
        int recipe = TagFixtures.recipe("Pasta", 40, 40, 700, 20);
        TagFixtures.ingredient(recipe, carrot);

        RecipeTagService.Plan before = service.plan();
        assertTrue(before.cuisineColumn().isEmpty());
        assertTrue(before.toText().contains("cuisine tag: OFF"));
        assertFalse(before.recipesPerTag().containsKey("Italienisch"));

        TestDatabase.apply(List.of(Path.of("src", "test", "resources", "migrations", "add_recipe_cuisine.sql")));
        TagFixtures.run("UPDATE recipe SET recipe_cuisine = ? WHERE recipe_id = ?", "Italienisch", recipe);

        RecipeTagService.Plan after = service.commit(service.plan());
        assertEquals("recipe_cuisine", after.cuisineColumn().orElseThrow());
        assertEquals(1, count("SELECT COUNT(*) FROM tag WHERE tag_name = 'Italienisch' AND tag_category = 'Küche'"));
        assertTrue(tags(recipe, "derived").contains("Italienisch"));

        TagFixtures.run("UPDATE recipe SET recipe_cuisine = NULL WHERE recipe_id = ?", recipe);
        service.commit(service.plan());
        assertFalse(tags(recipe, "derived").contains("Italienisch"), "the cuisine went, so did the tag");
    }

    @Test
    void animalProductsListNamesTheRuleForEachBlockingItem() {
        var byReason = service.animalProducts();
        assertTrue(byReason.get("not vegetarian: category meat").stream().anyMatch(i -> i.id() == unclassifiedSausage));
        assertTrue(byReason.get("not vegan: category dairy").stream().anyMatch(i -> i.id() == butter));
        assertFalse(byReason.get("not vegan: category dairy").stream().anyMatch(i -> i.id() == oat));
    }
}
