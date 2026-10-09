package de.dahaem.shared;

import de.dahaem.mealplanner.MealPlan;
import de.dahaem.mealplanner.MealPlanRepository;
import de.dahaem.shopping.AggregatedIngredient;
import de.dahaem.shopping.ShoppingListRepository;
import de.dahaem.stores.StoreOffer;
import de.dahaem.stores.StoreRepository;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dialect-sensitive paths of the SQLite -> MariaDB switch, against the real schema.sql on a
 * throwaway MariaDB: INSERT IGNORE upserts, generated keys, DATE and DATETIME round-trips,
 * ON DUPLICATE KEY UPDATE, and GROUP_CONCAT.
 */
class MariaDbDialectTest {

    private final StoreRepository stores = new StoreRepository();

    @BeforeEach
    void setUp() {
        TestDatabase.reset();
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    @Test
    void findOrCreateStoreIsAnIdempotentInsertIgnoreUpsert() throws SQLException {
        int first = stores.findOrCreateStore("Penny");
        int second = stores.findOrCreateStore("Penny");
        int other = stores.findOrCreateStore("ALDI Nord");

        assertEquals(first, second, "the second call must find the existing row, not insert a duplicate");
        assertTrue(other != first);
        assertEquals(2, count("SELECT COUNT(*) FROM store"));
    }

    @Test
    void startRunReturnsTheGeneratedKeyAndStoresADatetime() throws SQLException {
        int storeId = stores.findOrCreateStore("Penny");

        int runId = stores.startRun(storeId, "OFFERS");
        int secondRun = stores.startRun(storeId, "RANGE");

        assertTrue(runId > 0, "getGeneratedKeys() must return the AUTO_INCREMENT id");
        assertEquals(runId + 1, secondRun);
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT store_import_run_started_at, store_import_run_finished_at "
                             + "FROM store_import_run WHERE store_import_run_id = ?")) {
            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                LocalDateTime started = rs.getObject(1, LocalDateTime.class);
                assertNotNull(started, "LocalDateTime must bind to a DATETIME column (the old ISO 'T'+nanos string did not)");
                assertTrue(Math.abs(java.time.Duration.between(started, LocalDateTime.now()).toHours()) <= 2,
                        "stored as Germany wall-clock time, so at most a couple of hours from the JVM clock");
                assertEquals(null, rs.getObject(2));
            }
        }
    }

    @Test
    void finishRunRoundTripsFinishedAtAndStatus() throws SQLException {
        int storeId = stores.findOrCreateStore("Penny");
        int runId = stores.startRun(storeId, "OFFERS");

        stores.finishRun(runId, "OK", 3, 42, "done");

        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT store_import_run_started_at, store_import_run_finished_at, "
                     + "store_import_run_status, store_import_run_items FROM store_import_run")) {
            assertTrue(rs.next());
            LocalDateTime started = rs.getObject(1, LocalDateTime.class);
            LocalDateTime finished = rs.getObject(2, LocalDateTime.class);
            assertNotNull(finished);
            assertFalse(finished.isBefore(started));
            assertEquals("OK", rs.getString(3));
            assertEquals(42, rs.getInt(4));
        }
    }

    @Test
    void saveOffersRoundTripsDatesAndUpdatesInPlaceOnDuplicateKey() throws SQLException {
        int storeId = stores.findOrCreateStore("Penny");
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate until = LocalDate.of(2026, 10, 11);
        LocalDate pulled = LocalDate.of(2026, 10, 4);

        stores.saveOffers(storeId, List.of(offer("A1", 1.99, from, until)), pulled, "STORE");
        // Same (store, external id, valid_from, region) again: the row is refreshed, not duplicated.
        stores.saveOffers(storeId, List.of(offer("A1", 1.49, from, until.plusDays(1))), pulled.plusDays(1), "STORE");

        assertEquals(1, count("SELECT COUNT(*) FROM store_offer"));
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT store_offer_sale_price, store_offer_valid_from, "
                     + "store_offer_valid_until, store_offer_pulled_on FROM store_offer")) {
            assertTrue(rs.next());
            assertEquals(1.49, rs.getDouble(1), 0.0001);
            assertEquals(from, rs.getObject(2, LocalDate.class));
            assertEquals(until.plusDays(1), rs.getObject(3, LocalDate.class));
            assertEquals(pulled.plusDays(1), rs.getObject(4, LocalDate.class));
        }
    }

    @Test
    void mealPlanWeekStartDateRoundTripsAsALocalDate() {
        MealPlanRepository repository = new MealPlanRepository();
        LocalDate week = LocalDate.of(2026, 10, 5);

        int id = repository.create(week);

        assertTrue(id > 0, "generated key from the meal_plan AUTO_INCREMENT");
        MealPlan plan = repository.findAll().get(0);
        assertEquals(id, plan.getId());
        assertEquals(week, plan.getWeekStartDate());
    }

    @Test
    void groupConcatReturnsACommaJoinedListOfDistinctRecipeIds() throws SQLException {
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_quantity, inventory_item_unit, inventory_item_category) "
                + "VALUES (1, 'Salt', 'Salz', 0, 'g', 'Gewürze')");
        for (int recipeId : new int[] {1, 2}) {
            exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time) "
                    + "VALUES (" + recipeId + ", 'R" + recipeId + "', 2, 10, 5)");
            exec("INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, "
                    + "recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (" + recipeId + ", 1, 'g', 10)");
        }

        List<AggregatedIngredient> rows = new ShoppingListRepository().aggregateIngredients(List.of(1, 2));

        assertEquals(1, rows.size());
        assertEquals(20.0, rows.get(0).totalQuantity(), 0.0001);
        assertEquals(List.of(1, 2), rows.get(0).sourceRecipeIds().stream().sorted().toList());
        // The scaled variant uses CAST(... AS DOUBLE), which MariaDB requires instead of AS REAL.
        List<AggregatedIngredient> scaled = new ShoppingListRepository().aggregateIngredients(List.of(1, 2), 4);
        assertEquals(40.0, scaled.get(0).totalQuantity(), 0.0001);
    }

    private static StoreOffer offer(String id, double price, LocalDate from, LocalDate until) {
        return new StoreOffer(id, "Brand", "Milch", "1 l", price, 1, null, null, null, null, null,
                null, "Milch", "", from, until);
    }

    private static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
