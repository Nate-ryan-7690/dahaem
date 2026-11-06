package de.dahaem.mealplanner;

import de.dahaem.shared.Database;
import de.dahaem.tags.TagFixtures;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Planned meals by date (week read, add, edit / move, remove, preferred servings) against a throwaway MariaDB
 * (Testcontainers). The repository is never mocked.
 */
class PlannedMealServiceTest {

    // 2026-11-02 is a Monday
    private static final LocalDate MON = LocalDate.of(2026, 11, 2);
    private static final LocalDate WED = LocalDate.of(2026, 11, 4);
    private static final LocalDate SUN = LocalDate.of(2026, 11, 8);
    private static final LocalDate NEXT_MON = LocalDate.of(2026, 11, 9);

    private MealPlanService service;
    private RecipeService recipes;
    private int soup;
    private int stew;

    @BeforeEach
    void setUp() throws SQLException {
        TestDatabase.reset();
        service = new MealPlanService(new MealPlanRepository());
        recipes = new RecipeService(new RecipeRepository());
        soup = TagFixtures.recipe("Suppe", 10, 20, null, null);
        stew = TagFixtures.recipe("Eintopf", 10, 40, null, null);
        exec("UPDATE recipe SET recipe_thumbnail_url = 'https://example.test/suppe.jpg' WHERE recipe_id = " + soup);
    }

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    @Test
    void mondayOfAnyDayOfTheWeek() {
        assertEquals(MON, MealPlanService.mondayOf(MON));
        assertEquals(MON, MealPlanService.mondayOf(WED));
        assertEquals(MON, MealPlanService.mondayOf(SUN));
        assertEquals(NEXT_MON, MealPlanService.mondayOf(NEXT_MON));
    }

    @Test
    void aWeekWithoutAPlanIsSevenEmptyDays() {
        MealPlanService.Week week = service.week(WED);

        assertEquals(MON, week.weekStart());
        assertNull(week.planId());
        assertEquals(7, week.days().size());
        for (int i = 0; i < 7; i++) {
            assertEquals(MON.plusDays(i), week.days().get(i).date());
            assertTrue(week.days().get(i).meals().isEmpty());
        }
        assertEquals(0, service.allPlans().size(), "reading a week must not create a plan");
    }

    @Test
    void theFirstMealAddedCreatesThePlanOfItsWeek() {
        PlannedMeal meal = service.addMeal(soup, WED, "Abendessen", null, null, null);

        assertEquals(1, service.allPlans().size());
        assertEquals(MON, service.allPlans().get(0).getWeekStartDate());
        assertEquals(soup, meal.getRecipeId());
        assertEquals("Suppe", meal.getRecipeName());
        assertEquals("https://example.test/suppe.jpg", meal.getRecipeThumbnailUrl());
        assertEquals(WED, meal.getDate());
        assertEquals("Abendessen", meal.getLabel());
        assertNull(meal.getOwnServings());
        assertEquals(0, meal.getPosition());

        MealPlanService.Week week = service.week(MON);
        assertEquals(service.allPlans().get(0).getId(), week.planId());
        assertEquals(1, week.days().get(2).meals().size());
        assertEquals(meal.getEntryId(), week.days().get(2).meals().get(0).getEntryId());
    }

    @Test
    void theSameRecipeCanBePlannedManyTimesAndMealsKeepTheirOrderOnADay() {
        PlannedMeal first = service.addMeal(soup, WED, "Mittagessen", null, null, null);
        PlannedMeal second = service.addMeal(soup, WED, "Abendessen", 4, "mit Brot", null);
        PlannedMeal third = service.addMeal(stew, WED, null, null, null, null);

        assertEquals(3, new java.util.HashSet<>(List.of(first.getEntryId(), second.getEntryId(),
                third.getEntryId())).size());
        assertEquals(List.of(0, 1, 2), List.of(first.getPosition(), second.getPosition(), third.getPosition()));
        List<PlannedMeal> wednesday = service.week(WED).days().get(2).meals();
        assertEquals(List.of(first.getEntryId(), second.getEntryId(), third.getEntryId()),
                wednesday.stream().map(PlannedMeal::getEntryId).toList());
        assertEquals(4, wednesday.get(1).getOwnServings());
        assertEquals("mit Brot", wednesday.get(1).getNote());
        assertNull(wednesday.get(2).getLabel());
    }

    @Test
    void anExplicitPositionIsKeptAndTheNextMealGoesAfterTheHighestOne() {
        service.addMeal(soup, WED, "A", null, null, null);
        PlannedMeal far = service.addMeal(stew, WED, "B", null, null, 5);
        PlannedMeal last = service.addMeal(soup, WED, "C", null, null, null);

        assertEquals(5, far.getPosition());
        assertEquals(6, last.getPosition());
        assertEquals(List.of("A", "B", "C"),
                service.week(WED).days().get(2).meals().stream().map(PlannedMeal::getLabel).toList());
    }

    @Test
    void mealsOfDifferentWeeksGoToDifferentPlans() {
        service.addMeal(soup, SUN, null, null, null, null);
        service.addMeal(stew, NEXT_MON, null, null, null, null);

        assertEquals(2, service.allPlans().size());
        assertEquals(1, service.week(MON).days().get(6).meals().size());
        assertEquals(1, service.week(NEXT_MON).days().get(0).meals().size());
        assertTrue(service.week(MON).days().get(0).meals().isEmpty());
    }

    @Test
    void aRowMadeByTheOldEndpointShowsOnTheMondayOfItsWeek() throws SQLException {
        int planId = service.createWeeklyPlan(MON);
        service.addRecipe(planId, soup, "Abendessen");   // old way: no date, no servings

        PlannedMeal onMonday = service.week(MON).days().get(0).meals().get(0);
        assertEquals(MON, onMonday.getDate());
        assertEquals("Abendessen", onMonday.getLabel());
        assertNull(onMonday.getOwnServings());
        assertEquals(planId, onMonday.getPlanId());
    }

    @Test
    void editingServingsLabelAndNoteAndClearingThemAgain() {
        PlannedMeal meal = service.addMeal(soup, WED, "Abendessen", null, null, null);

        PlannedMeal edited = service.updateMeal(meal.getEntryId(),
                new MealChange().withServings(5).withLabel("  Snack ").withNote("Reste")).orElseThrow();
        assertEquals(5, edited.getOwnServings());
        assertEquals("Snack", edited.getLabel());
        assertEquals("Reste", edited.getNote());
        assertEquals(WED, edited.getDate());
        assertEquals(soup, edited.getRecipeId());

        PlannedMeal cleared = service.updateMeal(meal.getEntryId(),
                new MealChange().withServings(null).withLabel(" ").withNote(null)).orElseThrow();
        assertNull(cleared.getOwnServings());
        assertNull(cleared.getLabel());
        assertNull(cleared.getNote());
    }

    @Test
    void aFieldNotSentIsLeftAlone() {
        PlannedMeal meal = service.addMeal(soup, WED, "Abendessen", 3, "Notiz", null);

        PlannedMeal edited = service.updateMeal(meal.getEntryId(), new MealChange().withPosition(7)).orElseThrow();

        assertEquals(7, edited.getPosition());
        assertEquals(3, edited.getOwnServings());
        assertEquals("Abendessen", edited.getLabel());
        assertEquals("Notiz", edited.getNote());
    }

    @Test
    void movingToAnotherDayInTheSameWeekKeepsThePlanAndGoesLast() {
        PlannedMeal moving = service.addMeal(soup, WED, "Abendessen", null, null, null);
        service.addMeal(stew, SUN, null, null, null, null);

        PlannedMeal moved = service.updateMeal(moving.getEntryId(), new MealChange().withDate(SUN)).orElseThrow();

        assertEquals(SUN, moved.getDate());
        assertEquals(moving.getPlanId(), moved.getPlanId());
        assertEquals(1, moved.getPosition(), "goes last on the new day");
        assertEquals(1, service.allPlans().size());
        assertTrue(service.week(MON).days().get(2).meals().isEmpty());
        assertEquals(2, service.week(MON).days().get(6).meals().size());
    }

    @Test
    void movingIntoAnotherWeekMovesItToThatWeeksPlanAndCreatesItIfNeeded() {
        PlannedMeal moving = service.addMeal(soup, WED, "Abendessen", 4, null, null);

        PlannedMeal moved = service.updateMeal(moving.getEntryId(), new MealChange().withDate(NEXT_MON)).orElseThrow();

        assertEquals(NEXT_MON, moved.getDate());
        assertFalse(moving.getPlanId() == moved.getPlanId());
        assertEquals(2, service.allPlans().size());
        assertEquals(4, moved.getOwnServings());
        assertTrue(service.week(MON).days().get(2).meals().isEmpty());
        assertEquals(moved.getEntryId(), service.week(NEXT_MON).days().get(0).meals().get(0).getEntryId());
    }

    @Test
    void aMealCanBeSwitchedToAnotherRecipe() {
        PlannedMeal meal = service.addMeal(soup, WED, null, null, null, null);

        PlannedMeal switched = service.updateMeal(meal.getEntryId(), new MealChange().withRecipeId(stew)).orElseThrow();

        assertEquals(stew, switched.getRecipeId());
        assertEquals("Eintopf", switched.getRecipeName());
    }

    @Test
    void removingOneMealLeavesTheOthersOfTheSameRecipe() {
        PlannedMeal a = service.addMeal(soup, WED, "Mittag", null, null, null);
        PlannedMeal b = service.addMeal(soup, WED, "Abend", null, null, null);

        assertTrue(service.removeMeal(a.getEntryId()));
        assertFalse(service.removeMeal(a.getEntryId()), "second remove finds nothing");

        List<PlannedMeal> left = service.week(WED).days().get(2).meals();
        assertEquals(1, left.size());
        assertEquals(b.getEntryId(), left.get(0).getEntryId());
    }

    @Test
    void anUnknownMealIsEmptyNotAnError() {
        assertEquals(Optional.empty(), service.updateMeal(9999, new MealChange().withPosition(1)));
        assertFalse(service.removeMeal(9999));
    }

    @Test
    void badInputIsRefusedBeforeAnythingIsWritten() {
        assertThrows(IllegalArgumentException.class, () -> service.addMeal(soup, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.addMeal(soup, WED, null, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.addMeal(soup, WED, null, null, null, -1));
        PlannedMeal meal = service.addMeal(soup, WED, null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateMeal(meal.getEntryId(), new MealChange().withServings(0)));
        assertThrows(IllegalArgumentException.class, () -> service.updateMeal(meal.getEntryId(), new MealChange()));
        assertEquals(1, service.week(WED).days().get(2).meals().size());
    }

    @Test
    void preferredServingsFlowIntoTheMealAndCanBeClearedAgain() {
        PlannedMeal meal = service.addMeal(soup, WED, null, null, null, null);
        assertNull(meal.getRecipePreferredServings());

        assertTrue(recipes.setPreferredServings(soup, 6));
        assertEquals(6, service.findMeal(meal.getEntryId()).orElseThrow().getRecipePreferredServings());
        assertEquals(new MealServings.Resolved(6, "recipe"),
                MealServings.resolve(service.findMeal(meal.getEntryId()).orElseThrow(), 3));

        assertTrue(recipes.setPreferredServings(soup, 6), "the same number again still finds the recipe");
        assertTrue(recipes.setPreferredServings(soup, null));
        assertNull(service.findMeal(meal.getEntryId()).orElseThrow().getRecipePreferredServings());
        assertFalse(recipes.setPreferredServings(9999, 4));
        assertThrows(IllegalArgumentException.class, () -> recipes.setPreferredServings(soup, 0));
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
