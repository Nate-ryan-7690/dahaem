package de.dahaem.shopping;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.mealplanner.MealPlanRepository;
import de.dahaem.shared.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises ShoppingListService.generateFromMealPlan against a real, temporary SQLite file built
 * from the subset of schema.sql it needs (meal_plan/meal_plan_recipe, recipe/recipe_ingredient,
 * inventory_item, and the store tables, plus shopping_list/shopping_list_item themselves, all with
 * the exact column definitions schema.sql declares). The repository under test is never mocked:
 * only Database's target file is swapped out.
 */
class ShoppingListGenerationTest {

    private Path dbFile;
    private ShoppingListService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        dbFile = Files.createTempFile("dahaem-shopping-generation-test", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty("dahaem.db.path", dbFile.toAbsolutePath().toString());
        createSchema();
        service = new ShoppingListService(new ShoppingListRepository(), new MealPlanRepository(),
                new InventoryRepository());
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("dahaem.db.path");
        Files.deleteIfExists(dbFile);
    }

    @Test
    void producesOnlyTheMissingQuantityAfterSubtractingInventory() throws SQLException {
        exec("INSERT INTO inventory_item VALUES (1, 'Onion', 'Zwiebel', 100, 'g', 'Gemüse', NULL, NULL)");
        exec("INSERT INTO recipe VALUES (1, 'Suppe')");
        exec("INSERT INTO recipe_ingredient VALUES (1, 1, 'g', 250)");

        exec("INSERT INTO store VALUES (1, 'Aldi')");
        exec("INSERT INTO brand VALUES (1, 'Bio')");
        exec("INSERT INTO store_item VALUES (1, 1, 1, 1, 'g', 500, 0.99, 1, NULL, NULL, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);

        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));

        assertEquals(1, countRows("shopping_list_item WHERE shopping_list_id = " + shoppingListId));
        assertEquals(150, singleInt("SELECT shopping_list_item_quantity_needed FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId));
        assertNull(singleObject("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                        + "WHERE shopping_list_id = " + shoppingListId),
                "no store item is auto-picked at generation time; the user chooses later");
    }

    @Test
    void excludesAnItemAlreadyFullyInStock() throws SQLException {
        exec("INSERT INTO inventory_item VALUES (1, 'Onion', 'Zwiebel', 500, 'g', 'Gemüse', NULL, NULL)");
        exec("INSERT INTO inventory_item VALUES (2, 'Pasta', 'Nudeln', 0, 'g', 'Trockenware', NULL, NULL)");
        exec("INSERT INTO recipe VALUES (1, 'Pasta al Pomodoro')");
        exec("INSERT INTO recipe_ingredient VALUES (1, 1, 'g', 250)");
        exec("INSERT INTO recipe_ingredient VALUES (2, 1, 'g', 400)");

        exec("INSERT INTO store VALUES (1, 'Aldi')");
        exec("INSERT INTO brand VALUES (1, 'Bio')");
        exec("INSERT INTO store_item VALUES (1, 1, 1, 1, 'g', 500, 0.99, 1, NULL, NULL, NULL)");
        exec("INSERT INTO store_item VALUES (2, 1, 1, 2, 'g', 500, 1.49, 1, NULL, NULL, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);

        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));

        assertEquals(1, countRows("shopping_list_item WHERE shopping_list_id = " + shoppingListId),
                "onion is fully in stock (500 >= 250) and must be dropped; only pasta remains");
        assertNull(singleObject("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                        + "WHERE shopping_list_id = " + shoppingListId),
                "no store item is auto-picked at generation time; the user chooses later");
        assertEquals(400, singleInt("SELECT shopping_list_item_quantity_needed FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId));
    }

    @Test
    void persistsTheShoppingListLinkedToTheMealPlanWithItsItems() throws SQLException {
        exec("INSERT INTO inventory_item VALUES (1, 'Pasta', 'Nudeln', 100, 'g', 'Trockenware', NULL, NULL)");
        exec("INSERT INTO recipe VALUES (1, 'Pasta al Pomodoro')");
        exec("INSERT INTO recipe_ingredient VALUES (1, 1, 'g', 500)");

        exec("INSERT INTO store VALUES (2, 'Rewe')");
        exec("INSERT INTO brand VALUES (2, 'Barilla')");
        exec("INSERT INTO store_item VALUES (3, 2, 2, 1, 'g', 500, 1.49, 1, 0.0, NULL, NULL)");
        exec("INSERT INTO store_discount VALUES (1, 3, '2026-09-20', '2026-09-30', 0.99, 'promo', 1, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);

        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));

        assertEquals(1, countRows("shopping_list WHERE shopping_list_id = " + shoppingListId));
        assertEquals(planId, singleInt("SELECT shopping_list_meal_plan_id FROM shopping_list "
                + "WHERE shopping_list_id = " + shoppingListId));
        assertEquals("2026-09-25", singleString("SELECT shopping_list_created_date FROM shopping_list "
                + "WHERE shopping_list_id = " + shoppingListId));

        assertEquals(1, countRows("shopping_list_item WHERE shopping_list_id = " + shoppingListId));
        assertEquals(400, singleInt("SELECT shopping_list_item_quantity_needed FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId));
        assertNull(singleObject("SELECT shopping_list_item_is_discounted FROM shopping_list_item "
                        + "WHERE shopping_list_id = " + shoppingListId),
                "no store option is chosen yet at generation time, so whether it's on sale is unknown");
        assertFalse(singleBoolean("SELECT shopping_list_item_purchase_status FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId), "a freshly generated item is still pending");
        assertFalse(singleBoolean("SELECT shopping_list_item_is_manual FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId));
    }

    @Test
    void selectingAStoreItemPersistsTheChoiceAndClearingItPersistsToo() throws SQLException {
        exec("INSERT INTO inventory_item VALUES (1, 'Pasta', 'Nudeln', 0, 'g', 'Trockenware', NULL, NULL)");
        exec("INSERT INTO recipe VALUES (1, 'Pasta al Pomodoro')");
        exec("INSERT INTO recipe_ingredient VALUES (1, 1, 'g', 500)");

        exec("INSERT INTO store VALUES (2, 'Rewe')");
        exec("INSERT INTO brand VALUES (2, 'Barilla')");
        exec("INSERT INTO store_item VALUES (3, 2, 2, 1, 'g', 500, 1.49, 1, 0.0, NULL, NULL)");

        int planId = createPlan(LocalDate.of(2026, 9, 28));
        addRecipeToPlan(planId, 1);
        int shoppingListId = service.generateFromMealPlan(planId, LocalDate.of(2026, 9, 25));
        int itemId = singleInt("SELECT shopping_list_item_id FROM shopping_list_item "
                + "WHERE shopping_list_id = " + shoppingListId);

        assertEquals(shoppingListId, service.findShoppingListIdForItem(itemId).orElseThrow());

        service.selectStoreItem(itemId, 3);
        assertEquals(3, singleInt("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                + "WHERE shopping_list_item_id = " + itemId));

        service.selectStoreItem(itemId, null);
        assertNull(singleObject("SELECT shopping_list_item_store_item_id FROM shopping_list_item "
                + "WHERE shopping_list_item_id = " + itemId), "passing null clears the selection");
    }

    private int createPlan(LocalDate weekStart) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO meal_plan (meal_plan_week_start_date) VALUES (?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, weekStart.toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void addRecipeToPlan(int planId, int recipeId) throws SQLException {
        exec("INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, "
                + "meal_plan_recipe_meal_time) VALUES (" + recipeId + ", " + planId + ", 'dinner')");
    }

    private void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

    private int countRows(String fromAndWhere) throws SQLException {
        return singleInt("SELECT COUNT(*) FROM " + fromAndWhere);
    }

    private int singleInt(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String singleString(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private Object singleObject(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1);
        }
    }

    private boolean singleBoolean(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private void createSchema() throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");

            st.execute("CREATE TABLE inventory_item (" +
                    "inventory_item_id INTEGER PRIMARY KEY, " +
                    "inventory_item_name_english TEXT NOT NULL, " +
                    "inventory_item_name_deutsch TEXT, " +
                    "inventory_item_quantity INTEGER NOT NULL DEFAULT 0, " +
                    "inventory_item_unit TEXT NOT NULL, " +
                    "inventory_item_category TEXT NOT NULL, " +
                    "inventory_item_subcategory TEXT, " +
                    "inventory_item_earliest_exp_date DATE)");

            st.execute("CREATE TABLE recipe (" +
                    "recipe_id INTEGER PRIMARY KEY, " +
                    "recipe_name TEXT NOT NULL)");

            st.execute("CREATE TABLE recipe_ingredient (" +
                    "recipe_ingredient_item_id INTEGER NOT NULL REFERENCES inventory_item(inventory_item_id), " +
                    "recipe_ingredient_recipe_id INTEGER NOT NULL REFERENCES recipe(recipe_id), " +
                    "recipe_ingredient_unit TEXT NOT NULL, " +
                    "recipe_ingredient_quantity_needed INTEGER NOT NULL, " +
                    "PRIMARY KEY (recipe_ingredient_item_id, recipe_ingredient_recipe_id))");

            st.execute("CREATE TABLE meal_plan (" +
                    "meal_plan_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "meal_plan_week_start_date DATE NOT NULL UNIQUE)");

            st.execute("CREATE TABLE meal_plan_recipe (" +
                    "meal_plan_recipe_id INTEGER NOT NULL REFERENCES recipe(recipe_id), " +
                    "meal_plan_recipe_plan_id INTEGER NOT NULL REFERENCES meal_plan(meal_plan_id), " +
                    "meal_plan_recipe_meal_time TEXT, " +
                    "PRIMARY KEY (meal_plan_recipe_id, meal_plan_recipe_plan_id))");

            st.execute("CREATE TABLE store (" +
                    "store_id INTEGER PRIMARY KEY, " +
                    "store_name TEXT NOT NULL UNIQUE)");

            st.execute("CREATE TABLE brand (" +
                    "brand_id INTEGER PRIMARY KEY, " +
                    "brand_name TEXT NOT NULL UNIQUE)");

            st.execute("CREATE TABLE store_product (" +
                    "store_product_id INTEGER PRIMARY KEY, " +
                    "store_product_store_id INTEGER NOT NULL REFERENCES store(store_id), " +
                    "store_product_external_id TEXT NOT NULL, " +
                    "store_product_last_seen DATE NOT NULL, " +
                    "store_product_is_available BOOLEAN NOT NULL)");

            st.execute("CREATE TABLE store_item (" +
                    "store_item_id INTEGER PRIMARY KEY, " +
                    "store_item_brand_id INTEGER NOT NULL REFERENCES brand(brand_id), " +
                    "store_item_store_id INTEGER NOT NULL REFERENCES store(store_id), " +
                    "store_item_item_id INTEGER NOT NULL REFERENCES inventory_item(inventory_item_id), " +
                    "store_item_package_unit TEXT NOT NULL, " +
                    "store_item_package_size INTEGER NOT NULL, " +
                    "store_item_price REAL, " +
                    "store_item_is_available BOOLEAN NOT NULL, " +
                    "store_item_deposit REAL, " +
                    "store_item_store_product_id INTEGER REFERENCES store_product(store_product_id), " +
                    "store_item_external_id TEXT)");

            st.execute("CREATE TABLE store_discount (" +
                    "store_discount_id INTEGER PRIMARY KEY, " +
                    "store_discount_store_item_id INTEGER NOT NULL REFERENCES store_item(store_item_id), " +
                    "store_discount_start_date DATE NOT NULL, " +
                    "store_discount_end_date DATE NOT NULL, " +
                    "store_discount_price REAL NOT NULL, " +
                    "store_discount_notes TEXT, " +
                    "store_discount_deal_quantity INTEGER NOT NULL DEFAULT 1, " +
                    "store_discount_deal_price REAL)");

            // Exact column definitions from schema.sql: the tables this feature persists to.
            st.execute("CREATE TABLE \"shopping_list\" ( \"shopping_list_id\" INTEGER, " +
                    "\"shopping_list_meal_plan_id\" INTEGER, " +
                    "\"shopping_list_created_date\" DATE NOT NULL, " +
                    "\"shopping_list_status\" BOOLEAN NOT NULL, " +
                    "PRIMARY KEY(\"shopping_list_id\" AUTOINCREMENT), " +
                    "FOREIGN KEY(\"shopping_list_meal_plan_id\") REFERENCES \"meal_plan\"(\"meal_plan_id\"))");

            st.execute("CREATE TABLE \"shopping_list_item\"(\"shopping_list_item_id\" INTEGER, " +
                    "\"shopping_list_id\" INTEGER NOT NULL, " +
                    "\"shopping_list_item_store_item_id\" INTEGER, " +
                    "\"shopping_list_item_store_section\" TEXT, " +
                    "\"shopping_list_item_quantity_needed\" INTEGER NOT NULL, " +
                    "\"shopping_list_item_is_discounted\" BOOLEAN, " +
                    "\"shopping_list_item_purchase_status\" BOOLEAN NOT NULL, " +
                    "\"shopping_list_item_is_manual\" BOOLEAN NOT NULL, " +
                    "PRIMARY KEY(\"shopping_list_item_id\" AUTOINCREMENT), " +
                    "FOREIGN KEY(\"shopping_list_id\") REFERENCES \"shopping_list\"(\"shopping_list_id\"), " +
                    "FOREIGN KEY(\"shopping_list_item_store_item_id\") REFERENCES \"store_item\"(\"store_item_id\"))");
        }
    }
}
