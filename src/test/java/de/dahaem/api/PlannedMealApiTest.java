package de.dahaem.api;

import de.dahaem.shared.Database;
import de.dahaem.tags.TagFixtures;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The meal planner by date through the full Spring context on made-up data in a throwaway MariaDB
 * (Testcontainers): read a week, plan a recipe on a day, edit / move / remove a meal by its id, servings
 * resolution (meal, recipe preference, household, default) and the recipe's preferred servings.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class PlannedMealApiTest {

    private static int soup;
    private static int household;

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabase() throws SQLException {
        TestDatabase.reset();
        soup = TagFixtures.recipe("Suppe", 10, 20, null, null);
        exec("UPDATE recipe SET recipe_thumbnail_url = 'https://example.test/suppe.jpg' WHERE recipe_id = " + soup);
        exec("INSERT INTO household (household_name, household_people, created_date) VALUES ('Zuhause', 3, '2026-11-06')");
        household = id("SELECT household_id FROM household");
    }

    @BeforeEach
    void clearPlans() throws SQLException {
        exec("DELETE FROM shopping_list_item");
        exec("DELETE FROM shopping_list");
        exec("DELETE FROM meal_plan_recipe");
        exec("DELETE FROM meal_plan");
        exec("UPDATE recipe SET recipe_preferred_servings = NULL");
    }

    @AfterAll
    static void deleteDatabase() {
        TestDatabase.close();
    }

    @Test
    void anEmptyWeekIsSevenEmptyDaysStartingOnMonday() throws Exception {
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2031-01-06"))
                .andExpect(jsonPath("$.planId").value(nullValue()))
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].date").value("2031-01-06"))
                .andExpect(jsonPath("$.days[6].date").value("2031-01-12"))
                .andExpect(jsonPath("$.days[3].meals", hasSize(0)));
    }

    @Test
    void addingAMealCreatesThePlanAndShowsOnItsDayWithTheThumbnail() throws Exception {
        mvc.perform(post("/api/meal-plans/meals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\": " + soup + ", \"date\": \"2031-01-08\", \"label\": \"Abendessen\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryId").isNumber())
                .andExpect(jsonPath("$.recipeName").value("Suppe"))
                .andExpect(jsonPath("$.recipeThumbnailUrl").value("https://example.test/suppe.jpg"))
                .andExpect(jsonPath("$.date").value("2031-01-08"))
                .andExpect(jsonPath("$.label").value("Abendessen"))
                .andExpect(jsonPath("$.position").value(0));

        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-06"))
                .andExpect(jsonPath("$.planId").isNumber())
                .andExpect(jsonPath("$.days[2].meals", hasSize(1)))
                .andExpect(jsonPath("$.days[2].meals[0].recipeId").value(soup));
    }

    @Test
    void servingsResolveFromMealThenRecipeThenHouseholdThenDefault() throws Exception {
        // no household named: default 2
        int entry = addMeal("2031-01-08", null);
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-08"))
                .andExpect(jsonPath("$.householdPeople").value(nullValue()))
                .andExpect(jsonPath("$.days[2].meals[0].servings").value(2))
                .andExpect(jsonPath("$.days[2].meals[0].servingsSource").value("default"));

        // household named (3 people)
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-08").param("householdId", "" + household))
                .andExpect(jsonPath("$.householdPeople").value(3))
                .andExpect(jsonPath("$.days[2].meals[0].servings").value(3))
                .andExpect(jsonPath("$.days[2].meals[0].servingsSource").value("household"));

        // the recipe's preference beats the household
        mvc.perform(put("/api/recipes/{id}/preferred-servings", soup).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeId").value(soup))
                .andExpect(jsonPath("$.preferredServings").value(4));
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-08").param("householdId", "" + household))
                .andExpect(jsonPath("$.days[2].meals[0].servings").value(4))
                .andExpect(jsonPath("$.days[2].meals[0].servingsSource").value("recipe"))
                .andExpect(jsonPath("$.days[2].meals[0].recipePreferredServings").value(4));

        // the meal's own number beats both, and null puts it back
        mvc.perform(patch("/api/meal-plans/meals/{id}", entry).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\": 6}")
                        .param("householdId", "" + household))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servings").value(6))
                .andExpect(jsonPath("$.servingsSource").value("meal"))
                .andExpect(jsonPath("$.ownServings").value(6));
        mvc.perform(patch("/api/meal-plans/meals/{id}", entry).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\": null}")
                        .param("householdId", "" + household))
                .andExpect(jsonPath("$.servings").value(4))
                .andExpect(jsonPath("$.servingsSource").value("recipe"))
                .andExpect(jsonPath("$.ownServings").value(nullValue()));

        // clearing the recipe preference falls back to the household
        mvc.perform(put("/api/recipes/{id}/preferred-servings", soup).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredServings").value(nullValue()));
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-08").param("householdId", "" + household))
                .andExpect(jsonPath("$.days[2].meals[0].servingsSource").value("household"));
    }

    @Test
    void aMealCanBeMovedEditedAndRemovedByItsId() throws Exception {
        int entry = addMeal("2031-01-08", 5);

        mvc.perform(patch("/api/meal-plans/meals/{id}", entry).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"2031-01-13\", \"label\": \"Mittagessen\", \"note\": \"Reste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2031-01-13"))
                .andExpect(jsonPath("$.label").value("Mittagessen"))
                .andExpect(jsonPath("$.note").value("Reste"))
                .andExpect(jsonPath("$.ownServings").value(5));
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-08"))
                .andExpect(jsonPath("$.days[2].meals", hasSize(0)));
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-13"))
                .andExpect(jsonPath("$.weekStart").value("2031-01-13"))
                .andExpect(jsonPath("$.days[0].meals", hasSize(1)));

        mvc.perform(delete("/api/meal-plans/meals/{id}", entry).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-13"))
                .andExpect(jsonPath("$.days[0].meals", hasSize(0)));
        mvc.perform(delete("/api/meal-plans/meals/{id}", entry).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void badRequestsGetTheRightStatus() throws Exception {
        mvc.perform(post("/api/meal-plans/meals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"2031-01-08\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/meal-plans/meals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\": " + soup + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/meal-plans/meals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\": " + soup + ", \"date\": \"2031-01-08\", \"servings\": 0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/meal-plans/meals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\": 999999, \"date\": \"2031-01-08\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/meal-plans/week").param("date", "not-a-date"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/meal-plans/week").param("householdId", "999999"))
                .andExpect(status().isNotFound());

        int entry = addMeal("2031-01-08", null);
        mvc.perform(patch("/api/meal-plans/meals/{id}", entry).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/meal-plans/meals/{id}", entry).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"servings\": \"four\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/meal-plans/meals/{id}", entry).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"tomorrow\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/meal-plans/meals/{id}", 999999).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\": 1}"))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/recipes/{id}/preferred-servings", soup).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/recipes/{id}/preferred-servings", soup).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\": 0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/recipes/{id}/preferred-servings", 999999).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\": 4}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theShoppingListEndpointTakesAHouseholdAndStillValidatesPeople() throws Exception {
        addMeal("2031-01-08", null);
        int planId = id("SELECT MAX(meal_plan_id) FROM meal_plan");

        mvc.perform(post("/api/meal-plans/{id}/shopping-list", planId).with(csrf())
                        .param("householdId", "" + household))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shoppingListId").isNumber());
        mvc.perform(post("/api/meal-plans/{id}/shopping-list", planId).with(csrf()).param("householdId", "999999"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/meal-plans/{id}/shopping-list", planId).with(csrf()).param("people", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/meal-plans/{id}/shopping-list", 999999).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theOldPlanEndpointsStillWorkAlongsideTheNewOnes() throws Exception {
        addMeal("2031-01-08", null);
        mvc.perform(get("/api/meal-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].weekStartDate").value("2031-01-06"))
                .andExpect(jsonPath("$[0].recipes", hasSize(1)));
    }

    @Test
    void mealsOfPastDaysAreRefusedWith409() throws Exception {
        // a week long gone (2020-01-06 was a Monday) with one meal on its Wednesday, written straight into the table
        exec("INSERT INTO meal_plan (meal_plan_id, meal_plan_week_start_date) VALUES (900, '2020-01-06')");
        exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, meal_plan_recipe_date, "
                + "meal_plan_recipe_meal_time) VALUES (" + soup + ", 900, '2020-01-08', 'Abendessen')");
        int pastEntry = id("SELECT MAX(meal_plan_recipe_entry_id) FROM meal_plan_recipe");
        int futureEntry = addMeal("2031-01-08", null);

        mvc.perform(post("/api/meal-plans/meals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\": " + soup + ", \"date\": \"2020-01-08\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        mvc.perform(patch("/api/meal-plans/meals/{id}", pastEntry).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"servings\": 4}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/meal-plans/meals/{id}", futureEntry).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"2020-01-08\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/meal-plans/meals/{id}", pastEntry).with(csrf()))
                .andExpect(status().isConflict());

        // the past meal is still there, and the future one was not moved
        mvc.perform(get("/api/meal-plans/week").param("date", "2020-01-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[2].meals", hasSize(1)))
                .andExpect(jsonPath("$.days[2].meals[0].entryId").value(pastEntry));
        mvc.perform(get("/api/meal-plans/week").param("date", "2031-01-08"))
                .andExpect(jsonPath("$.days[2].meals", hasSize(1)));
    }

    // ---------- helpers ----------

    private int addMeal(String date, Integer servings) throws Exception {
        String body = "{\"recipeId\": " + soup + ", \"date\": \"" + date + "\""
                + (servings != null ? ", \"servings\": " + servings : "") + "}";
        mvc.perform(post("/api/meal-plans/meals").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return id("SELECT MAX(meal_plan_recipe_entry_id) FROM meal_plan_recipe");
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

    private static int id(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
