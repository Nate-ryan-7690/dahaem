package de.dahaem.api;

import de.dahaem.tags.TagFixtures;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/recipes and /api/recipes/{id} with diet rules, through the full Spring context on made-up recipes in a
 * throwaway MariaDB (Testcontainers). The rules are applied on the server: the list never contains a HIDDEN recipe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class DietFilterApiTest {

    private static int carrot;
    private static int champignon;
    private static int salami;
    private static int a;   // Aaa Gemüse
    private static int b;   // Bbb Schweinebraten
    private static int c;   // Ccc Rindersteak
    private static int d;   // Ddd Salamipizza
    private static int e;   // Eee Suppe mit Speck (an unmatched line)
    private static int f;   // Fff Gulasch mit Rotwein
    private static int g;   // Ggg Pilzpfanne

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabase() {
        TestDatabase.reset();
        carrot = TagFixtures.item("karotten", "vegetable", null);
        champignon = TagFixtures.item("champignons", "vegetable", null);
        int pork = TagFixtures.item("schweinefilet", "meat", "fresh meat");
        int beef = TagFixtures.item("rindersteak", "meat", "fresh meat");
        salami = TagFixtures.item("salami", "meat", "wurst & aufschnitt");
        int wine = TagFixtures.item("rotwein", "beverages", "wine");
        TagFixtures.animal(pork, "Schwein");
        TagFixtures.animal(beef, "Rind");
        TagFixtures.animalWithCertainty(salami, "Schwein", "possible");
        TagFixtures.animalWithCertainty(salami, "Rind", "possible");
        TagFixtures.animalWithCertainty(salami, "Geflügel", "possible");
        TagFixtures.alcohol(wine);

        a = TagFixtures.recipe("Aaa Gemüse", 10, 10, 300, 5);
        TagFixtures.ingredient(a, carrot);
        TagFixtures.tag(a, "vegetarisch", "Ernährung", "derived");
        TagFixtures.tag(a, "vegan", "Ernährung", "derived");
        b = TagFixtures.recipe("Bbb Schweinebraten", 10, 90, 700, 40);
        TagFixtures.ingredient(b, pork);
        TagFixtures.tag(b, "Schwein", "Tier", "derived");
        c = TagFixtures.recipe("Ccc Rindersteak", 10, 20, 600, 40);
        TagFixtures.ingredient(c, beef);
        TagFixtures.tag(c, "Rind", "Tier", "derived");
        d = TagFixtures.recipe("Ddd Salamipizza", 10, 20, 800, 30);
        TagFixtures.ingredient(d, salami);
        TagFixtures.ingredient(d, carrot);
        e = TagFixtures.recipe("Eee Suppe mit Speck", 10, 20, 300, 8);
        TagFixtures.ingredient(e, carrot);
        TagFixtures.unmatched(e, 2, "50 g Speck", "unmatched");
        TagFixtures.tag(e, "unklar", "Ernährung", "derived");
        f = TagFixtures.recipe("Fff Gulasch mit Rotwein", 10, 90, 700, 40);
        TagFixtures.ingredient(f, beef);
        TagFixtures.ingredient(f, wine);
        TagFixtures.tag(f, "Rind", "Tier", "derived");
        TagFixtures.tag(f, "Alkohol", "Inhalt", "derived");
        g = TagFixtures.recipe("Ggg Pilzpfanne", 10, 10, 300, 5);
        TagFixtures.ingredient(g, champignon);
        TagFixtures.tag(g, "vegetarisch", "Ernährung", "derived");
    }

    @AfterAll
    static void deleteDatabase() {
        TestDatabase.close();
    }

    @Test
    void withoutRulesTheListIsUnchangedAndHasNoDietField() throws Exception {
        mvc.perform(get("/api/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].diet").doesNotExist())
                .andExpect(jsonPath("$[*].id", contains(a, b, c, d, e, f, g)));
        mvc.perform(get("/api/recipes").param("includeUnclear", "true"))
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].diet").doesNotExist());
        mvc.perform(get("/api/recipes/{id}", a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diet").doesNotExist());
    }

    @Test
    void halalHidesPorkAndAlcoholAndMarksTheSalamiPizzaAnpassbar() throws Exception {
        mvc.perform(get("/api/recipes").param("diet", "halal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(a, c, g, d)))   // OK first, then anpassbar; unclear is left out
                .andExpect(jsonPath("$[0].diet.status").value("OK"))
                .andExpect(jsonPath("$[0].diet.hints", hasSize(0)))
                .andExpect(jsonPath("$[0].diet.softHits").doesNotExist())
                .andExpect(jsonPath("$[0].diet.unmatchedIngredients").doesNotExist())
                .andExpect(jsonPath("$[3].diet.status").value("ANPASSBAR"))
                .andExpect(jsonPath("$[3].diet.hints", hasSize(1)))
                .andExpect(jsonPath("$[3].diet.hints[0].itemId").value(salami))
                .andExpect(jsonPath("$[3].diet.hints[0].name").value("salami"))
                .andExpect(jsonPath("$[3].diet.hints[0].allowedAnimals", contains("Geflügel", "Rind")))
                .andExpect(jsonPath("$[3].diet.hints[0].possibleAnimals", contains("Geflügel", "Rind", "Schwein")));
    }

    @Test
    void includeUnclearAlsoReturnsTheFlaggedRecipe() throws Exception {
        mvc.perform(get("/api/recipes").param("diet", "halal").param("includeUnclear", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(a, c, g, d, e)))
                .andExpect(jsonPath("$[4].diet.status").value("UNCLEAR"));
        mvc.perform(get("/api/recipes").param("diet", "halal").param("includeUnclear", "false"))
                .andExpect(jsonPath("$[*].id", not(hasItem(e))));
    }

    @Test
    void vegetarischReturnsOnlyTheTaggedKnownRecipes() throws Exception {
        mvc.perform(get("/api/recipes").param("diet", "vegetarisch"))
                .andExpect(jsonPath("$[*].id", contains(a, g)));
        mvc.perform(get("/api/recipes").param("diet", "vegan"))
                .andExpect(jsonPath("$[*].id", contains(a)));
        mvc.perform(get("/api/recipes").param("diet", "vegetarisch").param("includeUnclear", "true"))
                .andExpect(jsonPath("$[*].id", contains(a, g, e)));
    }

    @Test
    void dietIsRepeatableAndTagsAndItemsAreHardRules() throws Exception {
        mvc.perform(get("/api/recipes").param("diet", "halal", "vegetarisch"))
                .andExpect(jsonPath("$[*].id", contains(a, g)));
        mvc.perform(get("/api/recipes").param("excludeTags", "Schwein,Rind"))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(a, g, d)))   // e is hidden unless includeUnclear
                .andExpect(jsonPath("$[?(@.id==" + d + ")].diet.status").value("ANPASSBAR"));
        mvc.perform(get("/api/recipes").param("excludeTags", "Schwein").param("excludeTags", "Rind")
                        .param("includeUnclear", "true"))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(a, g, d, e)));
        mvc.perform(get("/api/recipes").param("excludeItems", String.valueOf(champignon)))
                .andExpect(jsonPath("$[*].id", not(hasItem(g))))
                .andExpect(jsonPath("$[*].id", hasItem(a)));
        mvc.perform(get("/api/recipes").param("excludeItems", carrot + "," + champignon))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(b, c, f)));
    }

    @Test
    void softRulesKeepTheRecipeAndSortItAfterTheOthers() throws Exception {
        mvc.perform(get("/api/recipes").param("avoidTags", "Rind").param("avoidItems", String.valueOf(champignon)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[*].id", contains(a, b, e, c, d, f, g)))   // soft hits last (c, f, g; d only through a possible Rind), name order inside each group
                .andExpect(jsonPath("$[?(@.id==" + c + ")].diet.softHits.tags[0]").value("Rind"))
                .andExpect(jsonPath("$[?(@.id==" + g + ")].diet.softHits.items[0].name").value("champignons"))
                .andExpect(jsonPath("$[?(@.id==" + a + ")].diet.softHits").isEmpty())
                .andExpect(jsonPath("$[?(@.id==" + a + ")].diet.status").value("OK"));
    }

    @Test
    void anUnknownPresetIs400AndABadItemIdToo() throws Exception {
        mvc.perform(get("/api/recipes").param("diet", "paleo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("paleo")))
                .andExpect(jsonPath("$.message", containsString("halal, koscher, vegetarisch, vegan")));
        mvc.perform(get("/api/recipes/{id}", a).param("diet", "paleo"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recipes").param("excludeItems", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theDetailCarriesTheSameStatusHintsAndTheUnmatchedLines() throws Exception {
        mvc.perform(get("/api/recipes/{id}", e).param("diet", "halal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Eee Suppe mit Speck"))
                .andExpect(jsonPath("$.diet.status").value("UNCLEAR"))
                .andExpect(jsonPath("$.diet.unmatchedIngredients", hasSize(1)))
                .andExpect(jsonPath("$.diet.unmatchedIngredients[0].lineNo").value(2))
                .andExpect(jsonPath("$.diet.unmatchedIngredients[0].rawText").value("50 g Speck"))
                .andExpect(jsonPath("$.diet.unmatchedIngredients[0].reason").value("unmatched"));
        mvc.perform(get("/api/recipes/{id}", d).param("diet", "halal"))
                .andExpect(jsonPath("$.diet.status").value("ANPASSBAR"))
                .andExpect(jsonPath("$.diet.hints[0].allowedAnimals", contains("Geflügel", "Rind")))
                .andExpect(jsonPath("$.diet.unmatchedIngredients", hasSize(0)));
        mvc.perform(get("/api/recipes/{id}", b).param("diet", "halal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diet.status").value("HIDDEN"));
        mvc.perform(get("/api/recipes/{id}", a).param("diet", "vegan").param("people", "4"))
                .andExpect(jsonPath("$.diet.status").value("OK"))
                .andExpect(jsonPath("$.people").value(4));
        mvc.perform(get("/api/recipes/{id}", 999_999).param("diet", "halal"))
                .andExpect(status().isNotFound());
    }
}
