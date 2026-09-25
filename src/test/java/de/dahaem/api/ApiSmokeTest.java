package de.dahaem.api;

import de.dahaem.shared.Database;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the full Spring context and hits the API through MockMvc against a real, temporary
 * SQLite file built the same way setup_db.py builds the demo database (schema.sql, then the
 * inventory catalog, the sample recipes and the demo data). Nothing is mocked: only Database's
 * target file is swapped out via the dahaem.db.path system property.
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

    private static Path dbFile;

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void createDatabase() throws IOException, SQLException {
        dbFile = Files.createTempFile("dahaem-api-test", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty("dahaem.db.path", dbFile.toAbsolutePath().toString());
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            for (Path script : SCRIPTS) {
                st.executeUpdate(Files.readString(script, StandardCharsets.UTF_8));
            }
        }
    }

    @AfterAll
    static void deleteDatabase() throws IOException {
        System.clearProperty("dahaem.db.path");
        Files.deleteIfExists(dbFile);
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
