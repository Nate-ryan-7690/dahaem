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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/recipes as a search: envelope, default page size, search, filters, paging and the diet rules together,
 * through the full Spring context on made-up recipes in a throwaway MariaDB (Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class RecipeSearchApiTest {

    private static int carrot;
    private static int pork;
    private static int a;   // "Aaa Gemüse"      vegetarisch, schnell, 20 min
    private static int b;   // "Bbb Schweinebraten" Schwein, 100 min
    private static int c;   // "Ccc Möhrensuppe" vegetarisch, 20 min

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabase() {
        TestDatabase.reset();
        carrot = TagFixtures.item("karotten", "vegetable", null);
        pork = TagFixtures.item("schweinefilet", "meat", "fresh meat");
        TagFixtures.animal(pork, "Schwein");

        a = TagFixtures.recipe("Aaa Gemüse", 10, 10, 300, 5);
        TagFixtures.ingredient(a, carrot);
        TagFixtures.tag(a, "vegetarisch", "Ernährung", "derived");
        TagFixtures.tag(a, "schnell", "Zeit", "derived");
        b = TagFixtures.recipe("Bbb Schweinebraten", 10, 90, 700, 40);
        TagFixtures.ingredient(b, pork);
        TagFixtures.tag(b, "Schwein", "Tier", "derived");
        c = TagFixtures.recipe("Ccc Möhrensuppe", 10, 10, 250, 4);
        TagFixtures.ingredient(c, carrot);
        TagFixtures.tag(c, "vegetarisch", "Ernährung", "derived");
        for (int i = 10; i < 40; i++) {
            TagFixtures.recipe("Massenrezept " + i, 5, 5, null, null);   // 33 recipes in all, more than one default page
        }
    }

    @AfterAll
    static void deleteDatabase() {
        TestDatabase.close();
    }

    @Test
    void withoutParametersYouGetTheFirstDefaultPageNotEverything() throws Exception {
        mvc.perform(get("/api/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(24)))
                .andExpect(jsonPath("$.total").value(33))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(24))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(a))
                .andExpect(jsonPath("$.items[0].diet").doesNotExist());
        mvc.perform(get("/api/recipes").param("page", "2"))
                .andExpect(jsonPath("$.items", hasSize(9)));
    }

    @Test
    void theCardHasJustWhatAListNeedsAndTheDetailIsUnchanged() throws Exception {
        mvc.perform(get("/api/recipes").param("q", "gemüse"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(a))
                .andExpect(jsonPath("$.items[0].name").value("Aaa Gemüse"))
                .andExpect(jsonPath("$.items[0].prepTime").value(10))
                .andExpect(jsonPath("$.items[0].cookTime").value(10))
                .andExpect(jsonPath("$.items[0].totalTime").value(20))
                .andExpect(jsonPath("$.items[0].servings").value(2))
                .andExpect(jsonPath("$.items[0].servingsMax").isEmpty())
                .andExpect(jsonPath("$.items[0].thumbnailUrl").isEmpty())
                .andExpect(jsonPath("$.items[0].tags", contains("vegetarisch", "schnell")))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[0].calories").doesNotExist());
        mvc.perform(get("/api/recipes/{id}", a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aaa Gemüse"))
                .andExpect(jsonPath("$.ingredients", hasSize(1)))
                .andExpect(jsonPath("$.tags", hasSize(2)));
        mvc.perform(get("/api/recipes/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void searchAndFiltersCombine() throws Exception {
        mvc.perform(get("/api/recipes").param("ingredient", "karotten"))
                .andExpect(jsonPath("$.items[*].id", contains(a, c)))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/recipes").param("ingredient", "karotten").param("tags", "schnell"))
                .andExpect(jsonPath("$.items[*].id", contains(a)));
        mvc.perform(get("/api/recipes").param("tags", "vegetarisch").param("maxTime", "20").param("sort", "name"))
                .andExpect(jsonPath("$.items[*].id", contains(a, c)));
        mvc.perform(get("/api/recipes").param("minTime", "60"))
                .andExpect(jsonPath("$.items[*].id", contains(b)));
        mvc.perform(get("/api/recipes").param("servings", "2").param("q", "suppe"))
                .andExpect(jsonPath("$.items[*].id", contains(c)));
    }

    @Test
    void pagingWalksTheResultOnceAndAPastTheEndPageIsEmpty() throws Exception {
        mvc.perform(get("/api/recipes").param("pageSize", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].id", contains(a, b)))
                .andExpect(jsonPath("$.totalPages").value(17));
        mvc.perform(get("/api/recipes").param("pageSize", "2").param("page", "2"))
                .andExpect(jsonPath("$.items[0].id").value(c));
        mvc.perform(get("/api/recipes").param("pageSize", "2").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(33));
    }

    @Test
    void dietRulesPageTooAndHiddenRecipesAreNotCounted() throws Exception {
        mvc.perform(get("/api/recipes").param("excludeTags", "Schwein").param("includeUnclear", "true")
                        .param("pageSize", "2"))
                .andExpect(jsonPath("$.total").value(32))
                .andExpect(jsonPath("$.totalPages").value(16))
                .andExpect(jsonPath("$.items[*].id", contains(a, c)));   // OK first; the 30 without ingredients are UNCLEAR
    }

    @Test
    void dietRulesKeepTheStatusPerCardAndCombineWithSearchAndPaging() throws Exception {
        mvc.perform(get("/api/recipes").param("diet", "vegetarisch").param("q", "ccc"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(c))
                .andExpect(jsonPath("$.items[0].diet.status").value("OK"));
        mvc.perform(get("/api/recipes").param("diet", "vegetarisch").param("pageSize", "1").param("page", "2"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].diet.status").value("OK"));
        mvc.perform(get("/api/recipes").param("avoidTags", "Schwein").param("pageSize", "50"))
                .andExpect(jsonPath("$.total").value(33))
                .andExpect(jsonPath("$.items[32].id").value(b))   // soft hit sorts last
                .andExpect(jsonPath("$.items[32].diet.softHits.tags[0]").value("Schwein"));
    }

    @Test
    void badParametersAre400() throws Exception {
        for (String[] bad : new String[][]{{"page", "0"}, {"pageSize", "0"}, {"pageSize", "101"}, {"page", "abc"},
                {"sort", "random"}, {"minTime", "-1"}, {"servings", "0"}, {"diet", "paleo"}}) {
            mvc.perform(get("/api/recipes").param(bad[0], bad[1]))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/recipes").param("minTime", "30").param("maxTime", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("minTime")));
    }
}
