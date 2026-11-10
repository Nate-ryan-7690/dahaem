package de.dahaem.suggestions;

import de.dahaem.mealplanner.RecipeRepository;
import de.dahaem.shared.Database;
import de.dahaem.shared.PageRequest;
import de.dahaem.tags.TagRepository;
import de.dahaem.tags.TagService;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pantry-first suggestions against a real, throwaway MariaDB (the repository is never mocked): the coverage counts,
 * the ingredient lines with their offers, and the order, filters and paging of the service.
 */
class PantrySuggestionServiceTest {

    private SuggestionRepository repository;
    private PantrySuggestionService service;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() throws IOException, SQLException {
        TestDatabase.reset();
        repository = new SuggestionRepository();
        service = new PantrySuggestionService(repository, new RecipeRepository(), new TagService(new TagRepository()),
                Clock.systemDefaultZone());
        seedStore(1, "Aldi");
        seedStore(2, "Edeka");
        seedBrand(1, "TestBrand");
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    @Test
    void coverageCountsStockExpiryAndOffersAndLeavesOutToTaste() throws SQLException {
        seedRecipe(1, "Suppe");
        seedItem(10, "Onion", 2, today.plusDays(2));    // in stock, expires soon
        seedItem(11, "Carrot", 1, null);                 // in stock
        seedItem(12, "Leek", 0, null);                   // missing, on sale at Aldi
        seedItem(13, "Celery", 0, null);                 // missing, no offer
        seedItem(14, "Salt", 0, null);                   // "to taste": not counted
        seedIngredient(10, 1, false);
        seedIngredient(11, 1, false);
        seedIngredient(12, 1, false);
        seedIngredient(13, 1, false);
        seedIngredient(14, 1, true);
        seedStoreItem(100, 1, 12);
        seedDiscount(1000, 100, today.minusDays(1), today.plusDays(3), 0.99);

        PantryCoverage c = repository.coverageByRecipe(today.plusDays(7), today, "Aldi").get(0);

        assertEquals(4, c.needed());
        assertEquals(2, c.inStock());
        assertEquals(1, c.expiring());
        assertEquals(1, c.missingOnSale());
        assertEquals(1, c.missingOnSaleAtStore());
        assertEquals(0, repository.coverageByRecipe(today.plusDays(7), today, "Edeka").get(0).missingOnSaleAtStore());
        assertEquals(0, repository.coverageByRecipe(today.plusDays(7), today, null).get(0).missingOnSaleAtStore());
    }

    @Test
    void anExpiredOrNotYetStartedDiscountIsNotAnOffer() throws SQLException {
        seedRecipe(1, "Suppe");
        seedItem(12, "Leek", 0, null);
        seedItem(13, "Celery", 0, null);
        seedIngredient(12, 1, false);
        seedIngredient(13, 1, false);
        seedStoreItem(100, 1, 12);
        seedStoreItem(101, 1, 13);
        seedDiscount(1000, 100, today.minusDays(10), today.minusDays(1), 0.99);   // expired
        seedDiscount(1001, 101, today.plusDays(1), today.plusDays(5), 0.99);      // starts tomorrow

        assertEquals(0, repository.coverageByRecipe(today.plusDays(7), today, null).get(0).missingOnSale());
        assertTrue(repository.offersOnSale(List.of(12, 13), today).isEmpty());
    }

    @Test
    void ingredientLinesHaveTheirStatusAndMissingOnesCarryEveryOffer() throws SQLException {
        seedRecipe(1, "Suppe");
        seedItem(10, "Onion", 2, today.plusDays(2));
        seedItem(11, "Carrot", 1, null);
        seedItem(12, "Leek", 0, null);
        seedItem(14, "Salt", 0, null);
        seedIngredient(10, 1, false);
        seedIngredient(11, 1, false);
        seedIngredient(12, 1, false);
        seedIngredient(14, 1, true);
        seedStoreItem(100, 1, 12);   // Aldi
        seedStoreItem(101, 2, 12);   // Edeka
        seedStoreItem(102, 1, 12);   // a second, identical Aldi listing: shown once
        seedDiscount(1000, 100, today, today.plusDays(3), 0.99);
        seedDiscount(1001, 101, today, today.plusDays(3), 1.19);
        seedDiscount(1002, 102, today, today.plusDays(3), 0.99);

        PantrySuggestionService.Entry entry = service.suggest(null, null, null, null, null, null, PageRequest.of(1, 24))
                .items().get(0);

        assertEquals(PantryLine.Status.EXPIRING, line(entry, 10).status());
        assertEquals(PantryLine.Status.IN_STOCK, line(entry, 11).status());
        assertEquals(PantryLine.Status.MISSING, line(entry, 12).status());
        assertEquals(PantryLine.Status.TO_TASTE, line(entry, 14).status());
        List<PantryOffer> offers = line(entry, 12).offers();
        assertEquals(List.of("Aldi", "Edeka"), offers.stream().map(PantryOffer::storeName).toList(),
                "every store once, by name; identical listings collapsed");
        assertTrue(line(entry, 11).offers().isEmpty(), "only missing ingredients carry offers");
    }

    @Test
    void aFullyStockedRecipeComesBeforeOneThatIsAllOnSale() throws SQLException {
        seedRecipe(1, "AllOnSale");
        seedRecipe(2, "AllInStock");
        seedItem(10, "Pasta", 0, null);
        seedItem(11, "Rice", 1, null);
        seedIngredient(10, 1, false);
        seedIngredient(11, 2, false);
        seedStoreItem(100, 1, 10);
        seedDiscount(1000, 100, today, today.plusDays(3), 0.99);

        PantrySuggestionService.Result result = service.suggest(null, null, null, null, null, null, PageRequest.of(1, 24));

        assertEquals(List.of("AllInStock", "AllOnSale"), result.items().stream().map(e -> e.recipe().getName()).toList());
        assertEquals(2, result.total());
    }

    @Test
    void recipesThatNeitherUseTheStockNorAreOnSaleDoNotShow() throws SQLException {
        seedRecipe(1, "Nothing");
        seedItem(10, "Pasta", 0, null);
        seedIngredient(10, 1, false);

        assertEquals(0, service.suggest(null, null, null, null, null, null, PageRequest.of(1, 24)).total());
    }

    @Test
    void searchTimeAndStoreFiltersNarrowTheList() throws SQLException {
        seedRecipe(1, "Gemuesesuppe");
        seedRecipe(2, "Gemuesepfanne");
        seedItem(10, "Carrot", 1, null);
        seedIngredient(10, 1, false);
        seedIngredient(10, 2, false);

        assertEquals(2, service.suggest(null, null, null, null, null, null, PageRequest.of(1, 24)).total());
        assertEquals(1, service.suggest("suppe", null, null, null, null, null, PageRequest.of(1, 24)).total());
        assertEquals(0, service.suggest(null, 10, null, null, null, null, PageRequest.of(1, 24)).total(),
                "both recipes take 40 minutes");
        assertEquals(2, service.suggest(null, 45, null, null, null, null, PageRequest.of(1, 24)).total());
        assertEquals(0, service.suggest(null, null, "Aldi", null, null, null, PageRequest.of(1, 24)).total(),
                "no missing ingredient is on sale at Aldi");
    }

    @Test
    void pagingCutsPagesWithAnExactTotalAndAPagePastTheEndIsEmpty() throws SQLException {
        seedItem(10, "Carrot", 1, null);
        for (int i = 1; i <= 5; i++) {
            seedRecipe(i, "Rezept " + i);
            seedIngredient(10, i, false);
        }

        PantrySuggestionService.Result second = service.suggest(null, null, null, null, null, "name", PageRequest.of(2, 2));

        assertEquals(5, second.total());
        assertEquals(3, second.totalPages());
        assertEquals(List.of("Rezept 3", "Rezept 4"), second.items().stream().map(e -> e.recipe().getName()).toList());
        assertTrue(service.suggest(null, null, null, null, null, null, PageRequest.of(9, 2)).items().isEmpty());
    }

    private static PantryLine line(PantrySuggestionService.Entry entry, int itemId) {
        return entry.lines().stream().filter(l -> l.itemId() == itemId).findFirst().orElseThrow();
    }

    private void seedRecipe(int id, String name) throws SQLException {
        exec("INSERT INTO recipe (recipe_id, recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar) VALUES (" + id + ", '" + name + "', 4, 30, 10, NULL, NULL, NULL, NULL, NULL, NULL)");
    }

    private void seedItem(int id, String name, int quantity, LocalDate earliestExpDate) throws SQLException {
        String exp = earliestExpDate == null ? "NULL" : "'" + earliestExpDate + "'";
        exec("INSERT INTO inventory_item (inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory, inventory_item_earliest_exp_date) VALUES (" + id + ", '" + name + "', NULL, " + quantity + ", 'pcs', 'Misc', NULL, " + exp + ")");
    }

    private void seedIngredient(int itemId, int recipeId, boolean toTaste) throws SQLException {
        String quantity = toTaste ? "NULL" : "1";
        String unit = toTaste ? "''" : "'pcs'";
        exec("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) VALUES (" + itemId + ", " + recipeId + ", " + unit + ", " + quantity + ", " + (toTaste ? 1 : 0) + ")");
    }

    private void seedStore(int id, String name) throws SQLException {
        exec("INSERT INTO store (store_id, store_name) VALUES (" + id + ", '" + name + "')");
    }

    private void seedBrand(int id, String name) throws SQLException {
        exec("INSERT INTO brand (brand_id, brand_name) VALUES (" + id + ", '" + name + "')");
    }

    private void seedStoreItem(int storeItemId, int storeId, int itemId) throws SQLException {
        exec("INSERT INTO store_item (store_item_id, store_item_brand_id, store_item_store_id, " +
                "store_item_item_id, store_item_package_unit, store_item_package_size, store_item_price, " +
                "store_item_is_available, store_item_deposit) VALUES (" +
                storeItemId + ", 1, " + storeId + ", " + itemId + ", 'g', 500, 1.99, 1, NULL)");
    }

    private void seedDiscount(int id, int storeItemId, LocalDate start, LocalDate end, double price) throws SQLException {
        exec("INSERT INTO store_discount (store_discount_id, store_discount_store_item_id, " +
                "store_discount_start_date, store_discount_end_date, store_discount_price, store_discount_notes) " +
                "VALUES (" + id + ", " + storeItemId + ", '" + start + "', '" + end + "', " + price + ", NULL)");
    }

    private void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
