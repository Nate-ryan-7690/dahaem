package de.dahaem.api;

import de.dahaem.shared.Database;
import de.dahaem.tags.TagFixtures;
import de.dahaem.testsupport.TestDatabase;
import de.dahaem.testsupport.TestHousehold;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two households in one database, with the real token rule (the PHP front end names the user in X-Acting-User): each sees
 * and changes only its own meal plans, whatever ids or parameters a request carries.
 */
@SpringBootTest(properties = "dahaem.internal-token=test-secret-token")
@AutoConfigureMockMvc
class HouseholdSeparationApiTest {

    private static final String TOKEN = "test-secret-token";
    private static final String DAY = "2031-01-08";   // a Wednesday: day index 2 of the week of 2031-01-06

    private static int soup;
    private static int householdA;
    private static int userA;
    private static int userB;
    private static int userWithoutHousehold;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void createTwoHouseholds() throws SQLException {
        TestDatabase.reset();
        soup = TagFixtures.recipe("Suppe", 10, 20, null, null);
        householdA = TestHousehold.createHousehold("Haushalt A", 2);
        int householdB = TestHousehold.createHousehold("Haushalt B", 4);
        userA = TestHousehold.createUser(householdA, "a@example.test", "ADMIN");
        userB = TestHousehold.createUser(householdB, "b@example.test", "ADMIN");
        userWithoutHousehold = TestHousehold.createUserWithoutHousehold("nobody@example.test");
    }

    @AfterAll
    static void close() {
        TestDatabase.close();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, int user) {
        return request.header("X-Internal-Token", TOKEN).header("X-Acting-User", String.valueOf(user))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private int addMeal(int user) throws Exception {
        mvc.perform(as(post("/api/meal-plans/meals"), user)
                        .content("{\"recipeId\": " + soup + ", \"date\": \"" + DAY + "\"}"))
                .andExpect(status().isCreated());
        return id("SELECT MAX(meal_plan_recipe_entry_id) FROM meal_plan_recipe");
    }

    @Test
    void eachHouseholdPlansTheSameWeekOnItsOwnAndSeesOnlyItsOwnMeals() throws Exception {
        int mealOfA = addMeal(userA);
        int mealOfB = addMeal(userB);
        assertNotEquals(mealOfA, mealOfB);

        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[2].meals", hasSize(1)))
                .andExpect(jsonPath("$.days[2].meals[0].entryId").value(mealOfA));
        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userB))
                .andExpect(jsonPath("$.days[2].meals", hasSize(1)))
                .andExpect(jsonPath("$.days[2].meals[0].entryId").value(mealOfB));
        mvc.perform(as(get("/api/meal-plans"), userA)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(as(get("/api/meal-plans"), userB)).andExpect(jsonPath("$", hasSize(1)));
        // two plans for the same week, one per household
        org.junit.jupiter.api.Assertions.assertEquals(2, id("SELECT COUNT(*) FROM meal_plan WHERE meal_plan_week_start_date = '2031-01-06'"));
    }

    @Test
    void aWeekWithNoPlanOfOneHouseholdIsEmptyEvenIfAnotherHouseholdPlannedIt() throws Exception {
        addMeal(userA);
        int lonely = TestHousehold.createHousehold("Haushalt C", null);
        int userC = TestHousehold.createUser(lonely, "c@example.test", "MEMBER");

        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userC))
                .andExpect(jsonPath("$.planId").value(nullValue()))
                .andExpect(jsonPath("$.days[2].meals", hasSize(0)));
    }

    @Test
    void anotherHouseholdsMealCannotBeChangedOrRemoved() throws Exception {
        int mealOfA = addMeal(userA);

        mvc.perform(as(patch("/api/meal-plans/meals/{id}", mealOfA), userB).content("{\"servings\": 9}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/meal-plans/meals/{id}", mealOfA), userB))
                .andExpect(status().isNotFound());
        // still there for its own household, and untouched
        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userA))
                .andExpect(jsonPath("$.days[2].meals[0].ownServings").value(nullValue()));
        mvc.perform(as(delete("/api/meal-plans/meals/{id}", mealOfA), userA))
                .andExpect(status().isNoContent());
    }

    @Test
    void anotherHouseholdsPlanAndItsShoppingListsAreNotFound() throws Exception {
        addMeal(userA);
        int planOfA = id("SELECT meal_plan_id FROM meal_plan WHERE meal_plan_household_id = " + householdA
                + " AND meal_plan_week_start_date = '2031-01-06'");

        mvc.perform(as(get("/api/meal-plans/{id}/shopping-lists", planOfA), userB)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/meal-plans/{id}/shopping-list", planOfA), userB)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/meal-plans/{id}/recipes", planOfA), userB)
                        .content("{\"recipeId\": " + soup + ", \"mealTime\": \"Abendessen\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/meal-plans/{id}/recipes/{recipe}", planOfA, soup), userB))
                .andExpect(status().isNotFound());
        // its own household can
        mvc.perform(as(get("/api/meal-plans/{id}/shopping-lists", planOfA), userA)).andExpect(status().isOk());
    }

    @Test
    void theHeadCountIsTheCallersOwnAndNoRequestParameterChangesTheHousehold() throws Exception {
        addMeal(userA);
        addMeal(userB);

        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userA))
                .andExpect(jsonPath("$.householdPeople").value(2))
                .andExpect(jsonPath("$.days[2].meals[0].servings").value(2));
        // B asks with A's household id: still B's data and B's number
        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY).param("householdId", "" + householdA), userB))
                .andExpect(jsonPath("$.householdPeople").value(4))
                .andExpect(jsonPath("$.days[2].meals[0].servings").value(4));
    }

    @Test
    void theActingUserMustBeNamedKnownAndInAHousehold() throws Exception {
        mvc.perform(get("/api/meal-plans/week").header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/meal-plans/week").header("X-Internal-Token", TOKEN).header("X-Acting-User", "abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/meal-plans/week").header("X-Internal-Token", TOKEN).header("X-Acting-User", "99999"))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/meal-plans/week"), userWithoutHousehold))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_HOUSEHOLD"));
        // without the token the header is not believed at all
        mvc.perform(get("/api/meal-plans/week").header("X-Acting-User", "" + userA))
                .andExpect(status().isUnauthorized());
    }

    private int newList(int user) throws Exception {
        String body = mvc.perform(as(post("/api/shopping-lists"), user))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("shoppingListId").asInt();
    }

    @Test
    void eachHouseholdSeesOnlyItsOwnShoppingLists() throws Exception {
        int listOfA = newList(userA);
        int listOfB = newList(userB);

        mvc.perform(as(get("/api/shopping-lists"), userA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(listOfA));
        mvc.perform(as(get("/api/shopping-lists"), userB))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(listOfB));
    }

    @Test
    void anotherHouseholdsShoppingListCannotBeReadOrChanged() throws Exception {
        int listOfA = newList(userA);

        mvc.perform(as(get("/api/shopping-lists/{id}", listOfA), userB)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/shopping-lists/{id}/items", listOfA), userB)
                        .content("{\"name\": \"Backpapier\", \"quantity\": 1}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(put("/api/shopping-lists/{id}/archived", listOfA), userB).content("{\"archived\": true}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(put("/api/shopping-lists/{id}/status", listOfA), userB).content("{\"completed\": true}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/shopping-lists/{id}/items/{item}", listOfA, 1), userB))
                .andExpect(status().isNotFound());
        // untouched, and fine for its own household
        mvc.perform(as(get("/api/shopping-lists/{id}", listOfA), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void aListMadeFromAPlanBelongsToTheHouseholdOfThePlan() throws Exception {
        addMeal(userA);
        int planOfA = id("SELECT meal_plan_id FROM meal_plan WHERE meal_plan_household_id = " + householdA);
        String body = mvc.perform(as(post("/api/meal-plans/{id}/shopping-list", planOfA), userA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int list = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("shoppingListId").asInt();

        mvc.perform(as(get("/api/shopping-lists/{id}", list), userA)).andExpect(status().isOk());
        mvc.perform(as(get("/api/shopping-lists/{id}", list), userB)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/shopping-lists"), userB)).andExpect(jsonPath("$", hasSize(0)));
        org.junit.jupiter.api.Assertions.assertEquals(householdA,
                id("SELECT shopping_list_household_id FROM shopping_list WHERE shopping_list_id = " + list));
    }

    private static int id(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
