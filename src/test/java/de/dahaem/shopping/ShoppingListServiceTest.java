package de.dahaem.shopping;

import de.dahaem.shared.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the shopping-list slice against a real, temporary SQLite file built from the
 * subset of the schema it needs (recipe/recipe_ingredient/inventory_item + the store tables).
 * The repository under test is never mocked: only Database's target file is swapped out.
 */
class ShoppingListServiceTest {

    private Path dbFile;
    private ShoppingListService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        dbFile = Files.createTempFile("dahaem-shopping-test", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty("dahaem.db.path", dbFile.toAbsolutePath().toString());
        createSchema();
        service = new ShoppingListService(new ShoppingListRepository());
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("dahaem.db.path");
        Files.deleteIfExists(dbFile);
    }

    @Test
    void aggregatesIngredientQuantityAndRecipesAcrossRecipes() throws SQLException {
        exec("INSERT INTO inventory_item VALUES (1, 'Zwiebel', 'Onion', 'Gemüse')");
        exec("INSERT INTO recipe VALUES (1, 'Suppe')");
        exec("INSERT INTO recipe VALUES (2, 'Eintopf')");
        exec("INSERT INTO recipe_ingredient VALUES (1, 1, 'g', 100)");
        exec("INSERT INTO recipe_ingredient VALUES (2, 1, 'g', 150)");

        ShoppingList list = service.buildFromRecipes(List.of(1, 2));

        assertEquals(1, list.items().size());
        ShoppingListItem item = list.items().get(0);
        assertEquals(1, item.inventoryItemId());
        assertEquals("Zwiebel", item.nameDeutsch());
        assertEquals(250.0, item.totalQuantity());
        assertEquals("g", item.unit());
        assertTrue(item.sourceRecipeIds().containsAll(List.of(1, 2)));
        assertEquals(2, item.sourceRecipeIds().size());
        assertTrue(item.storeOptions().isEmpty(), "no store_item was seeded, so there are no options");
        assertNull(item.selectedStoreItemId(), "a freshly built line has no user selection yet");
    }

    @Test
    void collapsesDuplicateListingsOfTheSameProductByRecencyButKeepsOtherStoresSeparate() throws SQLException {
        exec("INSERT INTO inventory_item VALUES (2, 'Milch', 'Milk', 'Milchprodukte')");
        exec("INSERT INTO recipe VALUES (3, 'Pfannkuchen')");
        exec("INSERT INTO recipe_ingredient VALUES (3, 2, 'ml', 500)");

        exec("INSERT INTO store VALUES (1, 'Aldi')");
        exec("INSERT INTO brand VALUES (1, 'MilchMarke')");

        // A stale, no-longer-available relisting of the SAME product at the SAME store — must collapse away.
        exec("INSERT INTO store_product VALUES (1, 1, 'ext-old', '2026-01-01', 0)");
        exec("INSERT INTO store_item VALUES (1, 1, 1, 2, 'ml', 500, 0.89, 1, NULL, 1, NULL)");

        // The current, available listing for the same store/brand/pack, seen far more recently.
        exec("INSERT INTO store_product VALUES (2, 1, 'ext-new', '2026-09-20', 1)");
        exec("INSERT INTO store_item VALUES (2, 1, 1, 2, 'ml', 500, 1.29, 1, NULL, 2, NULL)");

        // A genuinely different store's option for the same item — must NOT be collapsed away.
        exec("INSERT INTO store VALUES (2, 'Rewe')");
        exec("INSERT INTO brand VALUES (2, 'AndereMarke')");
        exec("INSERT INTO store_item VALUES (3, 2, 2, 2, 'ml', 500, 0.79, 1, NULL, NULL, NULL)");

        ShoppingList list = service.buildFromRecipes(List.of(3), LocalDate.of(2026, 9, 25));

        assertEquals(1, list.items().size());
        List<StoreOption> options = list.items().get(0).storeOptions();
        assertEquals(2, options.size(), "the stale Aldi relisting is deduped, but Rewe's option is kept alongside it");
        assertNull(list.items().get(0).selectedStoreItemId(), "surfacing options must not auto-select one");

        // Cheapest till price first, but that is a display order, not a pre-made choice.
        assertEquals("Rewe", options.get(0).storeName());
        assertEquals(0.79, options.get(0).tillPrice(), 0.001);

        StoreOption aldiOption = options.stream().filter(o -> o.storeName().equals("Aldi")).findFirst().orElseThrow();
        assertEquals("MilchMarke", aldiOption.brandName());
        assertEquals(1.29, aldiOption.tillPrice(), 0.001, "must keep the current listing, not the cheaper stale one");
        assertTrue(!aldiOption.onSale());
    }

    @Test
    void prefersDealPriceOverNormalPriceWhenACurrentDiscountApplies() throws SQLException {
        exec("INSERT INTO inventory_item VALUES (3, 'Nudeln', 'Pasta', 'Trockenware')");
        exec("INSERT INTO recipe VALUES (4, 'Pasta al Pomodoro')");
        exec("INSERT INTO recipe_ingredient VALUES (4, 3, 'g', 500)");

        exec("INSERT INTO store VALUES (2, 'Rewe')");
        exec("INSERT INTO brand VALUES (2, 'Barilla')");
        exec("INSERT INTO store_item VALUES (3, 2, 2, 3, 'g', 500, 1.49, 1, 0.0, NULL, NULL)");
        exec("INSERT INTO store_discount VALUES (1, 3, '2026-09-20', '2026-09-30', 0.99, 'promo', 2, 1.80)");

        ShoppingList list = service.buildFromRecipes(List.of(4), LocalDate.of(2026, 9, 25));

        List<StoreOption> options = list.items().get(0).storeOptions();
        assertEquals(1, options.size());
        StoreOption option = options.get(0);
        assertTrue(option.onSale());
        assertEquals(0.99, option.salePrice(), 0.001);
        assertEquals(2026, option.validUntil().getYear());
        assertEquals(0.90, option.tillPrice(), 0.001, "deal price 1.80 for 2 => 0.90 per unit, no deposit");
    }

    private void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

    private void createSchema() throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute("CREATE TABLE inventory_item (" +
                    "inventory_item_id INTEGER PRIMARY KEY, " +
                    "inventory_item_name_deutsch TEXT, " +
                    "inventory_item_name_english TEXT, " +
                    "inventory_item_category TEXT)");

            st.execute("CREATE TABLE recipe (" +
                    "recipe_id INTEGER PRIMARY KEY, " +
                    "recipe_name TEXT)");

            st.execute("CREATE TABLE recipe_ingredient (" +
                    "recipe_ingredient_recipe_id INTEGER NOT NULL REFERENCES recipe(recipe_id), " +
                    "recipe_ingredient_item_id INTEGER NOT NULL REFERENCES inventory_item(inventory_item_id), " +
                    "recipe_ingredient_unit TEXT NOT NULL, " +
                    "recipe_ingredient_quantity_needed INTEGER NOT NULL)");

            st.execute("CREATE TABLE store (" +
                    "store_id INTEGER PRIMARY KEY, " +
                    "store_name TEXT NOT NULL)");

            st.execute("CREATE TABLE brand (" +
                    "brand_id INTEGER PRIMARY KEY, " +
                    "brand_name TEXT NOT NULL)");

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
        }
    }
}
