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

    @Test
    void thePreferredServingsOfARecipeAreEachHouseholdsOwn() throws Exception {
        addMeal(userA);
        addMeal(userB);

        mvc.perform(as(put("/api/recipes/{id}/preferred-servings", soup), userA).content("{\"servings\": 6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferredServings").value(6));
        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userA))
                .andExpect(jsonPath("$.days[2].meals[0].servings").value(6))
                .andExpect(jsonPath("$.days[2].meals[0].servingsSource").value("recipe"));
        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userB))
                .andExpect(jsonPath("$.days[2].meals[0].servings").value(4))
                .andExpect(jsonPath("$.days[2].meals[0].servingsSource").value("household"));

        mvc.perform(as(put("/api/recipes/{id}/preferred-servings", soup), userB).content("{\"servings\": 3}"))
                .andExpect(status().isOk());
        mvc.perform(as(put("/api/recipes/{id}/preferred-servings", soup), userB).content("{\"servings\": null}"))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userB))
                .andExpect(jsonPath("$.days[2].meals[0].servingsSource").value("household"));
        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), userA))
                .andExpect(jsonPath("$.days[2].meals[0].servings").value(6));
        mvc.perform(as(put("/api/recipes/{id}/preferred-servings", 999999), userA).content("{\"servings\": 2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aHouseholdCanOnlyReadAndChangeItsOwnHeadCount() throws Exception {
        int householdB = id("SELECT household_id FROM household_member WHERE user_id = " + userB);

        mvc.perform(as(get("/api/households/{id}", householdA), userA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.people").value(2));
        mvc.perform(as(get("/api/households/{id}", householdA), userB)).andExpect(status().isNotFound());
        mvc.perform(as(put("/api/households/{id}/people", householdA), userB).content("{\"people\": 9}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/households/{id}", householdA), userA)).andExpect(jsonPath("$.people").value(2));
        mvc.perform(as(put("/api/households/{id}/people", householdB), userB).content("{\"people\": 5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.people").value(5));
    }

    @Test
    void theMembersOfTheOwnHouseholdOnlyAndEmailsOnlyForAdminsAndYourself() throws Exception {
        int member = TestHousehold.createUser(householdA, "member@example.test", "MEMBER");

        mvc.perform(as(get("/api/household/members"), userA))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].you").value(true))
                .andExpect(jsonPath("$[0].email").value("a@example.test"))
                .andExpect(jsonPath("$[1].role").value("MEMBER"))
                .andExpect(jsonPath("$[1].email").value("member@example.test"));
        // the member sees the same people, but only their own address
        mvc.perform(as(get("/api/household/members"), member))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email").value(nullValue()))
                .andExpect(jsonPath("$[1].email").value("member@example.test"))
                .andExpect(jsonPath("$[1].you").value(true));
        // the other household sees only itself
        mvc.perform(as(get("/api/household/members"), userB))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("b@example.test"));
    }

    @Test
    void theTwentyFirstOpenInvitationAnswers409WithItsOwnCode() throws Exception {
        for (int i = 0; i < 20; i++) {
            inviteCode(userA, null);
        }
        mvc.perform(as(post("/api/household/invitations"), userA).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_LIMIT"));
        // the other household has its own limit
        inviteCode(userB, null);
    }

    private String inviteCode(int user, String note) throws Exception {
        String body = mvc.perform(as(post("/api/household/invitations"), user)
                        .content(note == null ? "{}" : "{\"note\": \"" + note + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("code").asText();
    }

    private static String acceptBody(String code, String email) {
        return "{\"code\": \"" + code + "\", \"email\": \"" + email
                + "\", \"displayName\": \"Anna\", \"password\": \"a long enough password\"}";
    }

    @Test
    void anInvitationMakesAMemberOfTheInvitingHouseholdOnly() throws Exception {
        String code = inviteCode(userA, "Anna");

        // nobody is logged in yet: the PHP sends only the token
        String body = mvc.perform(post("/api/invitations/accept").header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(acceptBody(code, "anna@example.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("anna@example.test"))
                .andExpect(jsonPath("$.household.id").value(householdA))
                .andExpect(jsonPath("$.household.role").value("MEMBER"))
                .andReturn().getResponse().getContentAsString();
        int anna = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("id").asInt();

        // she is in household A: sees its plan, not B's
        addMeal(userA);
        addMeal(userB);
        mvc.perform(as(get("/api/meal-plans/week").param("date", DAY), anna))
                .andExpect(jsonPath("$.days[2].meals", hasSize(1)));
        // the code works once
        mvc.perform(post("/api/invitations/accept").header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(acceptBody(code, "again@example.test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INVITATION"));
        // and without the token nothing is accepted at all
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody(inviteCode(userA, null), "no-token@example.test")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnAdminMakesListsAndRevokesInvitationsAndOnlyOfItsOwnHousehold() throws Exception {
        int member = TestHousehold.createUser(householdA, "member@example.test", "MEMBER");
        mvc.perform(as(post("/api/household/invitations"), member).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ADMIN"));
        mvc.perform(as(get("/api/household/invitations"), member)).andExpect(status().isForbidden());

        inviteCode(userA, "Anna");
        int inviteOfA = id("SELECT MAX(household_invite_id) FROM household_invite");
        mvc.perform(as(get("/api/household/invitations"), userA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].note").value("Anna"))
                .andExpect(jsonPath("$[0].code").doesNotExist());
        // the admin of the other household sees none and cannot revoke it
        mvc.perform(as(get("/api/household/invitations"), userB)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(delete("/api/household/invitations/{id}", inviteOfA), userB)).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/household/invitations/{id}", inviteOfA), userA)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/household/invitations"), userA)).andExpect(jsonPath("$[0].status").value("REVOKED"));
        mvc.perform(as(delete("/api/household/invitations/{id}", inviteOfA), userA)).andExpect(status().isNotFound());
    }

    @Test
    void badSignUpDataIsRefusedAndKeepsTheCodeUsable() throws Exception {
        String code = inviteCode(userA, null);

        mvc.perform(post("/api/invitations/accept").header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"" + code + "\", \"email\": \"x@example.test\", "
                                + "\"displayName\": \"X\", \"password\": \"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/invitations/accept").header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(acceptBody(code, "newbie@example.test")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/invitations/accept").header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"" + code + "\"}"))
                .andExpect(status().isBadRequest());
    }

    /** A milk (catalogue item 5001, counted in ml) that Aldi sells in packs of 1000 ml (store item 7001). */
    private static void milk() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_quantity, "
                + "inventory_item_unit, inventory_item_category) VALUES (5001, 'milk', 0, 'ml', 'dairy')");
        exec("INSERT INTO store (store_id, store_name) VALUES (1, 'Aldi')");
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (1, 'Gut')");
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, store_item_item_id, "
                + "store_item_package_unit, store_item_package_size, store_item_price, store_item_is_available) "
                + "VALUES (7001, 1, 1, 5001, 'ml', 1000, 1.19, 1)");
    }

    private void buyMilk(int user, int packs) throws Exception {
        mvc.perform(as(post("/api/purchases"), user)
                        .content("{\"storeItemId\": 7001, \"quantityPurchased\": " + packs
                                + ", \"pricePaid\": 2.50, \"expirationDate\": \"2031-02-01\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void eachHouseholdHasItsOwnStockOfTheSameCatalogueItem() throws Exception {
        milk();
        buyMilk(userA, 2);

        mvc.perform(as(get("/api/inventory"), userA))
                .andExpect(jsonPath("$[?(@.id == 5001)].quantity").value(2000));
        mvc.perform(as(get("/api/inventory"), userB))
                .andExpect(jsonPath("$[?(@.id == 5001)].quantity").value(0));
        mvc.perform(as(get("/api/inventory/search").param("q", "milk"), userB))
                .andExpect(jsonPath("$.items[0].quantity").value(0));
        mvc.perform(as(get("/api/inventory/expiring").param("days", "99999"), userA))
                .andExpect(jsonPath("$[?(@.id == 5001)]", hasSize(1)));
        mvc.perform(as(get("/api/inventory/expiring").param("days", "99999"), userB))
                .andExpect(jsonPath("$[?(@.id == 5001)]", hasSize(0)));
        mvc.perform(as(get("/api/inventory/low-stock").param("threshold", "0"), userB))
                .andExpect(jsonPath("$[?(@.id == 5001)]", hasSize(1)));
        mvc.perform(as(get("/api/inventory/low-stock").param("threshold", "0"), userA))
                .andExpect(jsonPath("$[?(@.id == 5001)]", hasSize(0)));
    }

    @Test
    void usingStockTakesOnlyFromTheCallersOwnStockAndBatches() throws Exception {
        milk();
        buyMilk(userA, 1);
        buyMilk(userB, 3);

        // B has 3000 ml, A 1000 ml: A cannot take more than its own
        mvc.perform(as(post("/api/inventory/5001/use"), userA).content("{\"quantity\": 1500}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/inventory/5001/use"), userA).content("{\"quantity\": 400}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(600));
        mvc.perform(as(get("/api/inventory"), userB))
                .andExpect(jsonPath("$[?(@.id == 5001)].quantity").value(3000));
        // each sees only its own batch, and what is left of it
        mvc.perform(as(get("/api/inventory/5001/batches"), userA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].remaining").value(600));
        mvc.perform(as(get("/api/inventory/5001/batches"), userB))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].remaining").value(3000));
        // a household that bought nothing has no batch and nothing to use
        int lonely = TestHousehold.createHousehold("Haushalt D", null);
        int userD = TestHousehold.createUser(lonely, "d@example.test", "MEMBER");
        mvc.perform(as(get("/api/inventory/5001/batches"), userD)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(post("/api/inventory/5001/use"), userD).content("{\"quantity\": 1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchasesAndSpendingAreTheCallersOwn() throws Exception {
        milk();
        buyMilk(userA, 1);
        buyMilk(userB, 1);
        buyMilk(userB, 1);

        mvc.perform(as(get("/api/purchases"), userA)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(as(get("/api/purchases"), userB)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(as(get("/api/analytics/spending"), userA)).andExpect(jsonPath("$.total").value(2.5));
        mvc.perform(as(get("/api/analytics/spending"), userB)).andExpect(jsonPath("$.total").value(5.0));
        mvc.perform(as(get("/api/analytics/spending").param("from", "2000-01-01").param("to", "2099-12-31"), userA))
                .andExpect(jsonPath("$.total").value(2.5));
    }

    @Test
    void theSameIntakeRequestIdOfTwoHouseholdsRecordsTwice() throws Exception {
        milk();
        exec("INSERT INTO product_barcode (product_barcode_code, product_barcode_item_id, product_barcode_package_unit, "
                + "product_barcode_package_size, product_barcode_created_date) VALUES ('4006381333931', 5001, 'ml', 1000, "
                + "'2026-11-01')");
        String body = "{\"requestId\": \"req-abcdefgh\", \"storeName\": \"Aldi\", \"lines\": [{\"barcode\": "
                + "\"4006381333931\", \"packs\": 1, \"expirationDate\": \"2031-02-01\", \"pricePaid\": 1.19}]}";

        mvc.perform(as(post("/api/intake"), userA).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.recorded").value(1));
        mvc.perform(as(post("/api/intake"), userB).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.recorded").value(1));
        mvc.perform(as(post("/api/intake"), userA).content(body))
                .andExpect(jsonPath("$.replayed").value(true));
        mvc.perform(as(get("/api/inventory"), userB))
                .andExpect(jsonPath("$[?(@.id == 5001)].quantity").value(1000));
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
