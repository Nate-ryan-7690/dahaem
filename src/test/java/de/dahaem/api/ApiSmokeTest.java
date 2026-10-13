package de.dahaem.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the full Spring context and hits the API through MockMvc against a real, temporary
 * throwaway MariaDB (Testcontainers) loaded the way the demo database is built (schema.sql, then the
 * inventory catalog, the sample recipes and the demo data). Nothing is mocked: only Database's
 * target is pointed at the throwaway container.
 *
 * <p>Every request runs as a logged-in user ({@code @WithMockUser}) and every write carries a CSRF
 * token, as the real frontend's would; the login itself is covered by AuthApiTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ApiSmokeTest {

    private static final List<Path> SCRIPTS = List.of(
            Path.of("schema.sql"),
            Path.of("seed", "inventory_catalog.sql"),
            Path.of("scripts", "recipes", "samples", "sample_recipes.sql"),
            Path.of("seed", "demo_data.sql"));

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabase() throws IOException, SQLException {
        TestDatabase.reset(SCRIPTS);
    }

    @AfterAll
    static void deleteDatabase() throws IOException {
        TestDatabase.close();
    }

    @Test
    void inventoryListsItemsAndFiltersValidateInput() throws Exception {
        mvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].nameEnglish").isString())
                .andExpect(jsonPath("$[0].quantity").isNumber());

        mvc.perform(get("/api/inventory/low-stock").param("threshold", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].quantity", everyItem(lessThanOrEqualTo(0))));

        mvc.perform(get("/api/inventory/expiring").param("days", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/inventory/expiring").param("days", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recipeDetailHasIngredientsAndInstructionsAndUnknownIdIs404() throws Exception {
        int recipeId = recipeId("Klassische Rinder-Bolognese");

        mvc.perform(get("/api/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[*].id", hasItem(recipeId)));

        mvc.perform(get("/api/recipes/{id}", recipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recipeId))
                .andExpect(jsonPath("$.name").value("Klassische Rinder-Bolognese"))
                .andExpect(jsonPath("$.nutrition").isMap())
                .andExpect(jsonPath("$.ingredients.length()", greaterThan(0)))
                .andExpect(jsonPath("$.ingredients[0].itemId").isNumber())
                .andExpect(jsonPath("$.instructions.length()", greaterThan(0)))
                .andExpect(jsonPath("$.instructions[0].stepNumber").isNumber());

        mvc.perform(get("/api/recipes/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void recipeDetailScalesIngredientsToPeople() throws Exception {
        // Seeded with recipe_servings = 2: 400 g beef (270) and 1 Stück onion (40), among others.
        int recipeId = recipeId("Klassische Rinder-Bolognese");

        mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servings").value(2))
                .andExpect(jsonPath("$.people").value(4))
                .andExpect(jsonPath("$.scalable").value(true))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].quantity", contains(400.0)))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].scaledQuantity", contains(800.0)))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(2.0)))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].unit", contains("g")))
                .andExpect(jsonPath("$.instructions[0].instruction")
                        .value("Zwiebel, Knoblauch und Karotte fein würfeln."))
                .andExpect(jsonPath("$.instructions[0].scaledInstruction")
                        .value("Zwiebel, Knoblauch und Karotte fein würfeln."));

        mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].quantity", contains(1.0)))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(1.5)))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].scaledQuantity", contains(600.0)));

        // people=1 of a 2-serving recipe: 1 Stück -> 0.5
        mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(0.5)));
    }

    @Test
    void recipeDetailWithoutPeopleIsUnscaledAndInvalidPeopleIs400() throws Exception {
        int recipeId = recipeId("Klassische Rinder-Bolognese");

        mvc.perform(get("/api/recipes/{id}", recipeId))
                .andExpect(status().isOk())
                // jsonPath treats a null value as absent, so check the raw body for the explicit null.
                .andExpect(content().string(containsString("\"people\":null")))
                .andExpect(jsonPath("$.scalable").value(true))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].quantity", contains(400.0)))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].scaledQuantity", contains(400.0)))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(1.0)));

        mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("'people' must be at least 1"));
        mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "-2"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recipes/{id}", 999_999).param("people", "4"))
                .andExpect(status().isNotFound());
    }

    @Test
    void recipeWithZeroServingsIsNotScalable() throws Exception {
        int recipeId = insertRecipe("Portionslos-Testrezept", 0);
        try {
            insertIngredient(recipeId, 270, "g", "300");
            mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.servings").value(0))
                    .andExpect(jsonPath("$.people").value(4))
                    .andExpect(jsonPath("$.scalable").value(false))
                    .andExpect(jsonPath("$.ingredients[0].quantity").value(300.0))
                    .andExpect(jsonPath("$.ingredients[0].scaledQuantity").value(300.0));
        } finally {
            deleteTestRecipe(recipeId);
        }
    }

    @Test
    void normalRecipeHasNoServingsMaxAndIgnoresBasisMax() throws Exception {
        // Seeded with recipe_servings = 2 and no range: 400 g beef (270), 1 Stück onion (40).
        int recipeId = recipeId("Klassische Rinder-Bolognese");

        mvc.perform(get("/api/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + recipeId + ")].servingsMax", contains(nullValue())));

        mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servings").value(2))
                .andExpect(jsonPath("$.servingsMax").value(nullValue()))
                .andExpect(jsonPath("$.basis").value("servings"))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].scaledQuantity", contains(800.0)));

        // basis=max on a recipe that is not a range is ignored: same factor 4/2, basis stays "servings"
        mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "4").param("basis", "max"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servingsMax").value(nullValue()))
                .andExpect(jsonPath("$.basis").value("servings"))
                .andExpect(jsonPath("$.scalable").value(true))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].scaledQuantity", contains(800.0)))
                .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(2.0)));
    }

    @Test
    void rangeRecipeScalesFromLowerBoundByDefaultAndFromMaxWithBasisMax() throws Exception {
        int recipeId = insertRecipe("Spanne-Testrezept", 3, 4);   // "3 – 4 Personen"
        try {
            insertIngredient(recipeId, 270, "g", "300");
            insertIngredient(recipeId, 40, "Stück", "1");

            mvc.perform(get("/api/recipes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + recipeId + ")].servings", contains(3)))
                    .andExpect(jsonPath("$[?(@.id == " + recipeId + ")].servingsMax", contains(4)));

            // default: people / servings = 2/3
            mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.servings").value(3))
                    .andExpect(jsonPath("$.servingsMax").value(4))
                    .andExpect(jsonPath("$.basis").value("servings"))
                    .andExpect(jsonPath("$.people").value(2))
                    .andExpect(jsonPath("$.scalable").value(true))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].quantity", contains(300.0)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].scaledQuantity", contains(200.0)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(0.67)));

            // basis=servings is the same as leaving it out
            mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "2").param("basis", "servings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.basis").value("servings"))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].scaledQuantity", contains(200.0)));

            // basis=max: people / servingsMax = 2/4; the stored quantity is unchanged
            mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "2").param("basis", "max"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.servings").value(3))
                    .andExpect(jsonPath("$.servingsMax").value(4))
                    .andExpect(jsonPath("$.basis").value("max"))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].quantity", contains(300.0)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 270)].scaledQuantity", contains(150.0)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(0.5)));

            mvc.perform(get("/api/recipes/{id}", recipeId).param("basis", "min"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            // the shopping list keeps scaling from servings (the lower bound): 300 g * 2/3
            mvc.perform(get("/api/shopping-list").param("recipeIds", String.valueOf(recipeId)).param("people", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.inventoryItemId == 270)].totalQuantity", contains(200.0)));
        } finally {
            deleteTestRecipe(recipeId);
        }
    }

    @Test
    void instructionsGainAScaledInstructionForPeopleAndBasis() throws Exception {
        // Made-up steps in HelloFresh's notation: 2-person value [3-person | 4-person].
        // Every amount keeps its own unit: 1 EL butter and 250 ml water are two separate amounts.
        String butter = "Erhitze 1 EL [1,5 EL | 2 EL] Butter* und gib 250 ml Wasser* dazu.";
        String varoma = "Gib 10 g [20 g] Öl* in den Mixtopf [und im Varoma-Behälter].";
        int recipeId = insertRecipe("Schritt-Mengen-Testrezept", 2);
        int rangeId = insertRecipe("Schritt-Mengen-Spanne-Testrezept", 3, 4);
        try {
            insertInstruction(recipeId, 1, butter);
            insertInstruction(recipeId, 2, varoma);
            insertInstruction(rangeId, 1, "Brate 300 g [450 g | 600 g] Hackfleisch an.");

            // without people: the original text, plus the bracket resolved to the 2-person value
            mvc.perform(get("/api/recipes/{id}", recipeId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructions[0].stepNumber").value(1))
                    .andExpect(jsonPath("$.instructions[0].instruction").value(butter))
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction")
                            .value("Erhitze 1 EL Butter* und gib 250 ml Wasser* dazu."))
                    .andExpect(jsonPath("$.instructions[1].scaledInstruction")
                            .value("Gib 10 g Öl* in den Mixtopf [und im Varoma-Behälter]."));

            // 3 people: HelloFresh's 3-person value; the single-value bracket has none, so 10 g is scaled
            mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructions[0].instruction").value(butter))
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction")
                            .value("Erhitze 1½ EL Butter* und gib 375 ml Wasser* dazu."))
                    .andExpect(jsonPath("$.instructions[1].scaledInstruction")
                            .value("Gib 15 g Öl* in den Mixtopf [und im Varoma-Behälter]."));

            mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction")
                            .value("Erhitze 2 EL Butter* und gib 500 ml Wasser* dazu."))
                    .andExpect(jsonPath("$.instructions[1].scaledInstruction")
                            .value("Gib 20 g Öl* in den Mixtopf [und im Varoma-Behälter]."));

            mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction")
                            .value("Erhitze ½ EL Butter* und gib 125 ml Wasser* dazu."));

            // a "3 – 4 Personen" range: a bracket always means 2 / 3 / 4 people; without people it follows the
            // recipe's own count (servings 3, or servingsMax 4 with basis=max), like the ingredient list
            mvc.perform(get("/api/recipes/{id}", rangeId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction").value("Brate 450 g Hackfleisch an."));
            mvc.perform(get("/api/recipes/{id}", rangeId).param("basis", "max"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction").value("Brate 600 g Hackfleisch an."));
            mvc.perform(get("/api/recipes/{id}", rangeId).param("people", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction").value("Brate 300 g Hackfleisch an."));
            mvc.perform(get("/api/recipes/{id}", rangeId).param("people", "2").param("basis", "max"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.basis").value("max"))
                    .andExpect(jsonPath("$.instructions[0].instruction")
                            .value("Brate 300 g [450 g | 600 g] Hackfleisch an."))
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction").value("Brate 300 g Hackfleisch an."));
            mvc.perform(get("/api/recipes/{id}", rangeId).param("people", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructions[0].scaledInstruction").value("Brate 600 g Hackfleisch an."));
        } finally {
            deleteTestRecipe(recipeId);
            deleteTestRecipe(rangeId);
        }
    }

    @Test
    void recipeScalingAgreesWithShoppingListScaling() throws Exception {
        assertRecipeAndShoppingListAgree(recipeId("Klassische Rinder-Bolognese"), 3);

        int recipeId = insertDecimalRecipe();
        try {
            for (int people = 1; people <= 5; people++) {
                assertRecipeAndShoppingListAgree(recipeId, people);
            }
        } finally {
            deleteTestRecipe(recipeId);
        }
    }

    @Test
    void recipeDetailHasDecimalQuantitiesAndToTasteIngredients() throws Exception {
        int recipeId = insertDecimalRecipe();
        try {
            mvc.perform(get("/api/recipes/{id}", recipeId).param("people", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 186)].quantity", contains(0.5)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 186)].scaledQuantity", contains(1.0)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 186)].toTaste", contains(false)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].quantity", contains(0.333)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(0.67)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 1022)].unit", contains("EL")))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 1022)].scaledQuantity", contains(4.0)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 1148)].toTaste", contains(true)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 1148)].quantity", contains(nullValue())))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 1148)].scaledQuantity", contains(nullValue())))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 1148)].unit", contains("")));

            mvc.perform(get("/api/recipes/{id}", recipeId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 186)].scaledQuantity", contains(0.5)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 40)].scaledQuantity", contains(0.333)))
                    .andExpect(jsonPath("$.ingredients[?(@.itemId == 1148)].scaledQuantity", contains(nullValue())));
        } finally {
            deleteTestRecipe(recipeId);
        }
    }

    @Test
    void shoppingListLeavesToTasteIngredientsOut() throws Exception {
        int recipeId = insertDecimalRecipe();
        try {
            mvc.perform(get("/api/shopping-list").param("recipeIds", String.valueOf(recipeId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(4)))
                    .andExpect(jsonPath("$.items[*].inventoryItemId", not(hasItem(1148))))
                    .andExpect(jsonPath("$.items[?(@.inventoryItemId == 186)].totalQuantity", contains(0.5)));
            mvc.perform(get("/api/shopping-list").param("recipeIds", String.valueOf(recipeId)).param("people", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(4)))
                    .andExpect(jsonPath("$.items[*].inventoryItemId", not(hasItem(1148))))
                    .andExpect(jsonPath("$.items[?(@.inventoryItemId == 186)].totalQuantity", contains(1.0)));
        } finally {
            deleteTestRecipe(recipeId);
        }
    }

    /** Every shopping-list line equals the recipe's scaledQuantity; to-taste ingredients are on neither side's list. */
    private void assertRecipeAndShoppingListAgree(int recipeId, int people) throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode recipe = json.readTree(mvc.perform(get("/api/recipes/{id}", recipeId).param("people", String.valueOf(people)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode list = json.readTree(mvc.perform(get("/api/shopping-list")
                        .param("recipeIds", String.valueOf(recipeId)).param("people", String.valueOf(people)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        int withQuantity = 0;
        for (JsonNode ingredient : recipe.get("ingredients")) {
            if (!ingredient.get("toTaste").asBoolean()) {
                withQuantity++;
            }
        }
        assertEquals(withQuantity, list.get("items").size());
        for (JsonNode line : list.get("items")) {
            double fromRecipe = -1;
            for (JsonNode ingredient : recipe.get("ingredients")) {
                if (ingredient.get("itemId").asInt() == line.get("inventoryItemId").asInt()
                        && ingredient.get("unit").asText().equals(line.get("unit").asText())) {
                    fromRecipe = ingredient.get("scaledQuantity").asDouble();
                }
            }
            assertEquals(line.get("totalQuantity").asDouble(), fromRecipe, 0.005,
                    "item " + line.get("inventoryItemId") + " scales differently on the recipe and the shopping list");
        }
    }

    @Test
    void tagsCanBeAddedAndListed() throws Exception {
        int recipeId = recipeId("Klassische Rinder-Bolognese");

        mvc.perform(post("/api/recipes/{id}/tags", recipeId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"api-smoke-tag\", \"category\": \"test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("api-smoke-tag"));

        mvc.perform(get("/api/recipes/{id}/tags", recipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("api-smoke-tag")));

        mvc.perform(post("/api/recipes/{id}/tags", recipeId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\": \"test\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recipes/{id}/tags", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void mealPlansCanBeCreatedAndFilled() throws Exception {
        int recipeId = recipeId("Gemüse-Gnocchi in Tomatensauce");

        mvc.perform(get("/api/meal-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[0].weekStartDate").isString())
                .andExpect(jsonPath("$[0].recipes").isArray());

        String created = mvc.perform(post("/api/meal-plans").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStartDate\": \"2031-01-06\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.weekStartDate").value("2031-01-06"))
                .andReturn().getResponse().getContentAsString();
        int planId = Integer.parseInt(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(post("/api/meal-plans/{id}/recipes", planId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\": " + recipeId + ", \"mealTime\": \"Abendessen\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipes", hasSize(1)))
                .andExpect(jsonPath("$.recipes[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.recipes[0].mealTime").value("Abendessen"));

        // Same week again violates meal_plan's UNIQUE week start; same recipe again violates the PK.
        mvc.perform(post("/api/meal-plans").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStartDate\": \"2031-01-06\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/meal-plans/{id}/recipes", planId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\": " + recipeId + ", \"mealTime\": \"Abendessen\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/meal-plans/{id}/recipes", 999_999).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\": " + recipeId + ", \"mealTime\": \"Abendessen\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/meal-plans").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStartDate\": \"not-a-date\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shoppingListIsBuiltFromRecipes() throws Exception {
        int first = recipeId("Klassische Rinder-Bolognese");
        int second = recipeId("Gemüse-Gnocchi in Tomatensauce");

        mvc.perform(get("/api/shopping-list").param("recipeIds", first + "," + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", greaterThan(0)))
                .andExpect(jsonPath("$.items[0].inventoryItemId").isNumber())
                .andExpect(jsonPath("$.items[0].totalQuantity").isNumber())
                .andExpect(jsonPath("$.items[0].sourceRecipeIds").isArray());

        mvc.perform(get("/api/shopping-list"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/shopping-list").param("recipeIds", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shoppingListPeopleParamScalesQuantitiesAndValidatesInput() throws Exception {
        int first = recipeId("Klassische Rinder-Bolognese");
        int second = recipeId("Gemüse-Gnocchi in Tomatensauce");

        String withoutPeople = mvc.perform(get("/api/shopping-list").param("recipeIds", first + "," + second))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String withPeople = mvc.perform(get("/api/shopping-list").param("recipeIds", first + "," + second)
                        .param("people", "4"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(firstTotalQuantity(withoutPeople) * 2, firstTotalQuantity(withPeople), 0.001,
                "recipe_servings is 2 for every seeded recipe, so people=4 must double the unscaled sum");

        mvc.perform(get("/api/shopping-list").param("recipeIds", first + "," + second).param("people", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shoppingListCanBeGeneratedPersistedReloadedAndSelectedFrom() throws Exception {
        int mealPlanId = 1; // seeded by demo_data.sql, with recipes attached

        String created = mvc.perform(post("/api/meal-plans/{id}/shopping-list", mealPlanId).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shoppingListId").isNumber())
                .andReturn().getResponse().getContentAsString();
        int shoppingListId = Integer.parseInt(created.replaceAll(".*\"shoppingListId\":(\\d+).*", "$1"));

        mvc.perform(get("/api/shopping-lists/{id}", shoppingListId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shoppingListId))
                .andExpect(jsonPath("$.mealPlanId").value(mealPlanId))
                .andExpect(jsonPath("$.items.length()", greaterThan(0)))
                .andExpect(jsonPath("$.items[0].shoppingListItemId").isNumber())
                .andExpect(jsonPath("$.items[0].inventoryItemId").isNumber())
                .andExpect(jsonPath("$.items[0].nameDeutsch").exists())
                .andExpect(jsonPath("$.items[0].nameEnglish").exists())
                .andExpect(jsonPath("$.items[0].quantityNeeded").isNumber())
                .andExpect(jsonPath("$.items[0].storeOptions").isArray())
                .andExpect(jsonPath("$.items[0].selectedStoreItemId").doesNotExist());

        int itemId = itemIdOnList(shoppingListId, 0);
        int inventoryItemId = inventoryItemIdForItem(itemId);
        int matchingStoreItemId = anyStoreItemIdForInventoryItem(inventoryItemId);
        int mismatchedStoreItemId = anyStoreItemIdForADifferentInventoryItem(inventoryItemId);

        mvc.perform(put("/api/shopping-lists/{listId}/items/{itemId}/store-item", shoppingListId, itemId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeItemId\": " + mismatchedStoreItemId + "}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/shopping-lists/{listId}/items/{itemId}/store-item", shoppingListId, itemId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeItemId\": " + matchingStoreItemId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeItemId").value(matchingStoreItemId));

        mvc.perform(get("/api/shopping-lists/{id}", shoppingListId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].selectedStoreItemId").value(matchingStoreItemId));

        mvc.perform(post("/api/meal-plans/{id}/shopping-list", 999_999).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/shopping-lists/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void generateShoppingListPeopleParamIsAcceptedAndValidated() throws Exception {
        int mealPlanId = 1; // seeded by demo_data.sql, with recipes attached

        mvc.perform(post("/api/meal-plans/{id}/shopping-list", mealPlanId).with(csrf()).param("people", "4"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shoppingListId").isNumber());

        mvc.perform(post("/api/meal-plans/{id}/shopping-list", mealPlanId).with(csrf()).param("people", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchasesCanBeRecordedAndSpendingIsTotalled() throws Exception {
        int storeItemId = anyStoreItemId();

        mvc.perform(post("/api/purchases").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeItemId\": " + storeItemId + ", \"purchaseDate\": \"2031-02-03\","
                                + " \"quantityPurchased\": 2, \"pricePaid\": 3.50, \"expirationDate\": \"2031-03-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.storeItemId").value(storeItemId))
                .andExpect(jsonPath("$.wasDiscounted").value(false))
                .andExpect(jsonPath("$.pricePaid").value(3.50));

        mvc.perform(get("/api/purchases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].purchaseDate", hasItem("2031-02-03")));

        mvc.perform(get("/api/analytics/spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber());
        mvc.perform(get("/api/analytics/spending").param("from", "2031-02-01").param("to", "2031-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2031-02-01"))
                .andExpect(jsonPath("$.total").value(3.50));
        mvc.perform(get("/api/analytics/spending").param("from", "2031-02-01"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/purchases").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeItemId\": " + storeItemId + ", \"quantityPurchased\": 0,"
                                + " \"pricePaid\": 1.0, \"expirationDate\": \"2031-03-01\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/purchases").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeItemId\": 999999, \"quantityPurchased\": 1,"
                                + " \"pricePaid\": 1.0, \"expirationDate\": \"2031-03-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void suggestionsSurfaceRecipesBySavingsAndByExpiring() throws Exception {
        // Klassische Rinder-Bolognese uses onion (40) and olive oil (1022), both under a
        // current-window discount in demo_data.sql, so it must be surfaced by-savings.
        int discountedRecipeId = recipeId("Klassische Rinder-Bolognese");

        mvc.perform(get("/api/suggestions/by-savings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[*].recipeId", hasItem(discountedRecipeId)))
                .andExpect(jsonPath("$[*].score", everyItem(greaterThan(0))))
                .andExpect(jsonPath("$[0].score", greaterThan(0)));

        setEarliestExpDate(40, LocalDate.now().plusDays(2));
        try {
            mvc.perform(get("/api/suggestions/by-expiring").param("days", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].recipeId", hasItem(discountedRecipeId)))
                    .andExpect(jsonPath("$[*].matchingItemIds[*]", hasItem(40)));

            mvc.perform(get("/api/suggestions/by-expiring").param("days", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
            mvc.perform(get("/api/suggestions/by-expiring").param("days", "0"))
                    .andExpect(status().isBadRequest());
        } finally {
            setEarliestExpDate(40, null);
        }
    }

    private static void setEarliestExpDate(int inventoryItemId, LocalDate date) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE inventory_item SET inventory_item_earliest_exp_date = ? WHERE inventory_item_id = ?")) {
            ps.setString(1, date == null ? null : date.toString());
            ps.setInt(2, inventoryItemId);
            ps.executeUpdate();
        }
    }

    private static double firstTotalQuantity(String shoppingListJson) {
        Matcher m = Pattern.compile("\"totalQuantity\":(-?\\d+(\\.\\d+)?)").matcher(shoppingListJson);
        if (!m.find()) {
            throw new IllegalStateException("No totalQuantity found in " + shoppingListJson);
        }
        return Double.parseDouble(m.group(1));
    }

    private static int insertRecipe(String name, int servings) throws SQLException {
        return insertRecipe(name, servings, null);
    }

    private static int insertRecipe(String name, int servings, Integer servingsMax) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO recipe (recipe_name, recipe_servings, "
                     + "recipe_servings_max, recipe_cook_time, recipe_prep_time) VALUES (?, ?, ?, 10, 0)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, servings);
            ps.setObject(3, servingsMax, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static void insertInstruction(int recipeId, int stepNumber, String text) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO recipe_instructions (recipe_instructions_recipe_id, "
                     + "recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_source) "
                     + "VALUES (?, ?, ?, 'Testrezept')")) {
            ps.setInt(1, recipeId);
            ps.setInt(2, stepNumber);
            ps.setString(3, text);
            ps.executeUpdate();
        }
    }

    private static void insertIngredient(int recipeId, int itemId, String unit, String quantity) throws SQLException {
        insertIngredient(recipeId, itemId, unit, new BigDecimal(quantity), false);
    }

    private static void insertToTaste(int recipeId, int itemId) throws SQLException {
        insertIngredient(recipeId, itemId, "", null, true);
    }

    private static void insertIngredient(int recipeId, int itemId, String unit, BigDecimal quantity, boolean toTaste)
            throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, "
                     + "recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed, "
                     + "recipe_ingredient_to_taste) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, recipeId);
            ps.setInt(2, itemId);
            ps.setString(3, unit);
            ps.setBigDecimal(4, quantity);
            ps.setBoolean(5, toTaste);
            ps.executeUpdate();
        }
    }

    /** A 2-serving recipe with decimal amounts, a spoon line and a to-taste line, as the fixed importer stores them. */
    private static int insertDecimalRecipe() throws SQLException {
        int recipeId = insertRecipe("Zitronen-Hähnchen-Testrezept", 2);
        insertIngredient(recipeId, 186, "Stück", "0.5");    // ½ Stück Zitrone
        insertIngredient(recipeId, 40, "Stück", "0.333");   // ⅓ Stück Zwiebel
        insertIngredient(recipeId, 1022, "EL", "2");        // 2 EL Olivenöl
        insertIngredient(recipeId, 270, "g", "250");
        insertToTaste(recipeId, 1148);                      // Salz nach Geschmack
        return recipeId;
    }

    // Throwaway test database only: removes the recipe this test inserted so other tests don't see it.
    private static void deleteTestRecipe(int recipeId) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ingredients = con.prepareStatement(
                     "DELETE FROM recipe_ingredient WHERE recipe_ingredient_recipe_id = ?");
             PreparedStatement instructions = con.prepareStatement(
                     "DELETE FROM recipe_instructions WHERE recipe_instructions_recipe_id = ?");
             PreparedStatement recipe = con.prepareStatement("DELETE FROM recipe WHERE recipe_id = ?")) {
            ingredients.setInt(1, recipeId);
            ingredients.executeUpdate();
            instructions.setInt(1, recipeId);
            instructions.executeUpdate();
            recipe.setInt(1, recipeId);
            recipe.executeUpdate();
        }
    }

    private static int recipeId(String name) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT recipe_id FROM recipe WHERE recipe_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Seed data has no recipe named " + name);
                }
                return rs.getInt(1);
            }
        }
    }

    private static int anyStoreItemId() throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT MIN(store_item_id) FROM store_item")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int itemIdOnList(int shoppingListId, int index) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT shopping_list_item_id FROM shopping_list_item "
                     + "WHERE shopping_list_id = ? ORDER BY shopping_list_item_id LIMIT 1 OFFSET ?")) {
            ps.setInt(1, shoppingListId);
            ps.setInt(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int inventoryItemIdForItem(int shoppingListItemId) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT shopping_list_item_inventory_item_id "
                     + "FROM shopping_list_item WHERE shopping_list_item_id = ?")) {
            ps.setInt(1, shoppingListItemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int anyStoreItemIdForInventoryItem(int inventoryItemId) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT MIN(store_item_id) FROM store_item WHERE store_item_item_id = ?")) {
            ps.setInt(1, inventoryItemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int anyStoreItemIdForADifferentInventoryItem(int inventoryItemId) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT MIN(store_item_id) FROM store_item WHERE store_item_item_id != ?")) {
            ps.setInt(1, inventoryItemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
