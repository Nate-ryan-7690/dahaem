package de.dahaem.mealplanner;

import de.dahaem.shared.Database;
import de.dahaem.tags.TagFixtures;
import de.dahaem.tags.TagRepository;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SQL of the recipe search (RecipeRepository.countMatching / findPage / findMatchingIds / findByIds and
 * TagRepository.tagNamesForRecipes) on made-up recipes in a throwaway MariaDB (Testcontainers). Read-only after
 * setup, so one database serves all tests.
 */
class RecipeSearchRepositoryTest {

    private static final RecipeRepository REPOSITORY = new RecipeRepository();

    // name, prep + cook = total, servings
    private static int suppe;       // "Karotten Suppe"      10 + 10 = 20, 2   Karotten, Zwiebeln;  vegan, schnell
    private static int nudeln;      // "Kaese Nudeln"         5 + 25 = 30, 2   Gouda;               vegetarisch, schnell
    private static int kuchen;      // "Zwiebel Kuchen"      20 + 60 = 80, 2   Zwiebeln, Gouda;     vegetarisch
    private static int unbekannt;   // "Unbekannte Zeit"      0 +  0 =  0, 2   (nothing)
    private static int saft;        // "100% Saft_Spezial"    5 +  0 =  5, 3 - 4
    private static int doppel1;     // "Doppel"              10 + 10 = 20, 2   same name and time as doppel2
    private static int doppel2;

    @BeforeAll
    static void createDatabase() throws SQLException {
        TestDatabase.reset();
        int karotten = TagFixtures.item("karotten", "vegetable", null);
        int zwiebeln = TagFixtures.item("zwiebeln", "vegetable", null);
        int gouda = TagFixtures.item("gouda", "dairy", null);

        suppe = TagFixtures.recipe("Karotten Suppe", 10, 10, 300, 5);
        TagFixtures.ingredient(suppe, karotten);
        TagFixtures.ingredient(suppe, zwiebeln);
        TagFixtures.tag(suppe, "vegan", "Ernährung", "derived");
        TagFixtures.tag(suppe, "schnell", "Zeit", "derived");
        nudeln = TagFixtures.recipe("Kaese Nudeln", 5, 25, 600, 20);
        TagFixtures.ingredient(nudeln, gouda);
        TagFixtures.tag(nudeln, "vegetarisch", "Ernährung", "derived");
        TagFixtures.tag(nudeln, "schnell", "Zeit", "derived");
        kuchen = TagFixtures.recipe("Zwiebel Kuchen", 20, 60, 500, 15);
        TagFixtures.ingredient(kuchen, zwiebeln);
        TagFixtures.ingredient(kuchen, gouda);
        TagFixtures.tag(kuchen, "vegetarisch", "Ernährung", "derived");
        unbekannt = TagFixtures.recipe("Unbekannte Zeit", 0, 0, null, null);
        saft = TagFixtures.recipe("100% Saft_Spezial", 5, 0, null, null);
        exec("UPDATE recipe SET recipe_servings = 3, recipe_servings_max = 4 WHERE recipe_id = ?", saft);
        doppel1 = TagFixtures.recipe("Doppel", 10, 10, null, null);
        doppel2 = TagFixtures.recipe("Doppel", 10, 10, null, null);
        exec("UPDATE recipe SET recipe_rating_avg = 4.5, recipe_rating_count = 10, "
                + "recipe_published_date = '2024-01-01' WHERE recipe_id = ?", nudeln);
        exec("UPDATE recipe SET recipe_rating_avg = 4.5, recipe_rating_count = 99, "
                + "recipe_published_date = '2025-06-01' WHERE recipe_id = ?", suppe);
    }

    @AfterAll
    static void deleteDatabase() {
        TestDatabase.close();
    }

    private static void exec(String sql, int id) throws SQLException {
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static RecipeQuery query(String q, List<String> ingredients, List<String> tags, Integer min, Integer max,
                                     Integer servings, String sort) {
        return RecipeQuery.of(q, ingredients, tags, min, max, servings, sort);
    }

    private static List<Integer> ids(RecipeQuery q) {
        List<Integer> viaPage = REPOSITORY.findPage(q, 100, 0).stream().map(Recipe::getId).toList();
        assertEquals(viaPage, REPOSITORY.findMatchingIds(q), "page query and id query agree");
        assertEquals(viaPage.size(), REPOSITORY.countMatching(q), "count agrees");
        return viaPage;
    }

    private static List<Integer> sorted(List<Integer> ids) {
        return ids.stream().sorted().toList();
    }

    @Test
    void noFilterReturnsEverythingByNameWithTheIdAsTieBreak() {
        assertEquals(7, REPOSITORY.countMatching(RecipeQuery.none()));
        assertEquals(List.of(saft, doppel1, doppel2, nudeln, suppe, unbekannt, kuchen), ids(RecipeQuery.none()));
    }

    @Test
    void nameSearchMatchesEveryWordInAnyOrderIgnoringCase() {
        assertEquals(List.of(suppe), ids(query("suppe", null, null, null, null, null, null)));
        assertEquals(List.of(suppe), ids(query("SUPPE karotten", null, null, null, null, null, null)));
        assertEquals(List.of(nudeln), ids(query("nudeln kaese", null, null, null, null, null, null)));
        assertEquals(List.of(), ids(query("suppe nudeln", null, null, null, null, null, null)));
        assertEquals(List.of(), ids(query("gibtesnicht", null, null, null, null, null, null)));
    }

    @Test
    void wildcardsInTheSearchTextAreLiteralAndSqlInTheTextIsInert() {
        assertEquals(List.of(saft), ids(query("%", null, null, null, null, null, null)));
        assertEquals(List.of(saft), ids(query("_", null, null, null, null, null, null)));
        assertEquals(List.of(), ids(query("'; DROP TABLE recipe; --", null, null, null, null, null, null)));
        assertEquals(7, REPOSITORY.countMatching(RecipeQuery.none()));   // the table is still there
    }

    @Test
    void ingredientSearchMatchesPartOfTheItemNameAndEveryIngredientMustBePresent() {
        assertEquals(sorted(List.of(suppe, kuchen)), sorted(ids(query(null, List.of("zwiebel"), null, null, null, null, null))));
        assertEquals(List.of(kuchen), ids(query(null, List.of("zwiebel", "goud"), null, null, null, null, null)));
        assertEquals(List.of(), ids(query(null, List.of("karotten", "gouda"), null, null, null, null, null)));
        assertEquals(List.of(suppe), ids(query(null, List.of("Karotten (en)"), null, null, null, null, null)));   // English name
    }

    @Test
    void nameAndIngredientCombine() {
        assertEquals(List.of(kuchen), ids(query("kuchen", List.of("gouda"), null, null, null, null, null)));
        assertEquals(List.of(), ids(query("suppe", List.of("gouda"), null, null, null, null, null)));
    }

    @Test
    void tagsFilterByAllNamedTagsAndAnUnknownTagMatchesNothing() {
        assertEquals(List.of(nudeln, suppe), ids(query(null, null, List.of("schnell"), null, null, null, null)));
        assertEquals(List.of(suppe), ids(query(null, null, List.of("schnell", "vegan"), null, null, null, null)));
        assertEquals(List.of(nudeln, kuchen), ids(query(null, null, List.of("vegetarisch"), null, null, null, null)));
        assertEquals(List.of(), ids(query(null, null, List.of("vegan", "vegetarisch"), null, null, null, null)));
        assertEquals(List.of(), ids(query(null, null, List.of("gibtesnicht"), null, null, null, null)));
    }

    @Test
    void timeFilterUsesPrepPlusCookAndLeavesOutRecipesWithoutATime() {
        assertEquals(List.of(saft, doppel1, doppel2, nudeln, suppe), ids(query(null, null, null, null, 30, null, null)));
        assertEquals(List.of(nudeln, kuchen), ids(query(null, null, null, 25, null, null, null)));
        assertEquals(List.of(doppel1, doppel2, nudeln, suppe), ids(query(null, null, null, 20, 30, null, null)));
        assertEquals(List.of(nudeln), ids(query(null, null, null, 30, 30, null, null)));   // bounds are inclusive
    }

    @Test
    void servingsMatchesRecipesForThatManyPeopleIncludingARange() {
        assertEquals(List.of(saft), ids(query(null, null, null, null, null, 3, null)));
        assertEquals(List.of(saft), ids(query(null, null, null, null, null, 4, null)));
        assertEquals(List.of(doppel1, doppel2, nudeln, suppe, unbekannt, kuchen), ids(query(null, null, null, null, null, 2, null)));
        assertEquals(List.of(), ids(query(null, null, null, null, null, 5, null)));
    }

    @Test
    void filtersCombine() {
        assertEquals(List.of(suppe), ids(query("suppe", List.of("karotten"), List.of("schnell"), null, 30, 2, null)));
        assertEquals(List.of(), ids(query("suppe", List.of("karotten"), List.of("schnell"), null, 10, 2, null)));
    }

    @Test
    void sortsAreTotalOrdersWithUnknownValuesLast() {
        assertEquals(List.of(saft, doppel1, doppel2, suppe, nudeln, kuchen, unbekannt),
                ids(query(null, null, null, null, null, null, "time")));
        assertEquals(List.of(suppe, nudeln, saft, doppel1, doppel2, unbekannt, kuchen),
                ids(query(null, null, null, null, null, null, "rating")));   // 4.5 / 99 votes before 4.5 / 10, unrated by name
        assertEquals(List.of(suppe, nudeln, saft, doppel1, doppel2, unbekannt, kuchen),
                ids(query(null, null, null, null, null, null, "newest")));
    }

    @Test
    void pagesCoverTheResultOnceInTheSameOrderEvenForRecipesWithTheSameNameAndTime() {
        RecipeQuery q = query(null, null, null, null, null, null, "time");
        List<Integer> all = REPOSITORY.findMatchingIds(q);
        List<Integer> viaPages = new ArrayList<>();
        for (int offset = 0; offset < 8; offset += 2) {
            viaPages.addAll(REPOSITORY.findPage(q, 2, offset).stream().map(Recipe::getId).toList());
        }
        assertEquals(all, viaPages);
        assertEquals(List.of(), REPOSITORY.findPage(q, 2, 7));
        assertEquals(1, REPOSITORY.findPage(q, 2, 6).size());
    }

    @Test
    void findByIdsKeepsTheGivenOrderAndSkipsUnknownIds() {
        List<Recipe> found = REPOSITORY.findByIds(List.of(kuchen, 999_999, suppe));
        assertEquals(List.of(kuchen, suppe), found.stream().map(Recipe::getId).toList());
        assertEquals("Zwiebel Kuchen", found.get(0).getName());
        assertEquals(List.of(), REPOSITORY.findByIds(List.of()));
    }

    @Test
    void tagNamesForRecipesCoversJustTheAskedRecipesSortedByGroupThenName() {
        TagRepository tags = new TagRepository();
        Map<Integer, List<String>> names = tags.tagNamesForRecipes(List.of(suppe, nudeln, unbekannt));
        assertEquals(List.of("vegan", "schnell"), names.get(suppe));   // group Ernährung before Zeit
        assertEquals(List.of("vegetarisch", "schnell"), names.get(nudeln));
        assertTrue(!names.containsKey(unbekannt));
        assertTrue(!names.containsKey(kuchen));
        assertTrue(tags.tagNamesForRecipes(List.of()).isEmpty());
    }
}
