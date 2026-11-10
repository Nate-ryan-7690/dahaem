package de.dahaem.shopping;

import de.dahaem.shared.Database;
import de.dahaem.shared.Portions;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * All SQL for building a shopping list lives here: summing recipe_ingredient across recipes,
 * finding store_item candidates (with brand/store and any current store_discount) for one
 * inventory item, and persisting a generated list to shopping_list/shopping_list_item. The
 * rest of the shopping slice never writes SQL; it calls these methods.
 */
public class ShoppingListRepository {

    /**
     * Sums recipe_ingredient_quantity_needed per (inventory item, unit) across the given recipes,
     * and collects which recipes contributed. Quantities are only combined when the unit matches,
     * so the same item can come back as more than one row if recipes disagree on unit. No scaling:
     * each recipe's own (decimal) quantities are summed as written. "To taste" rows (salt, pepper
     * without an amount) are left out entirely: they never add a quantity to a shopping list, the
     * inventory's low-stock view covers restocking them.
     */
    public List<AggregatedIngredient> aggregateIngredients(List<Integer> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = recipeIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT ri.recipe_ingredient_item_id AS item_id, "
                + "ii.inventory_item_name_deutsch AS name_deutsch, "
                + "ri.recipe_ingredient_unit AS unit, "
                + "SUM(ri.recipe_ingredient_quantity_needed) AS total_quantity, "
                + "GROUP_CONCAT(DISTINCT ri.recipe_ingredient_recipe_id) AS recipe_ids "
                + "FROM recipe_ingredient ri "
                + "JOIN inventory_item ii ON ii.inventory_item_id = ri.recipe_ingredient_item_id "
                + "WHERE ri.recipe_ingredient_recipe_id IN (" + placeholders + ") "
                + "AND ri.recipe_ingredient_to_taste = FALSE "
                + "GROUP BY ri.recipe_ingredient_item_id, ri.recipe_ingredient_unit "
                + "ORDER BY ii.inventory_item_name_deutsch";

        return runAggregateQuery(sql, recipeIds, Optional.empty());
    }

    /**
     * Same as {@link #aggregateIngredients(List)}, but scales each recipe's ingredient quantities
     * by {@code people / recipe_servings} before summing across recipes — so a 2-serving recipe's
     * ingredients double at {@code people=4}. Only recipes with a positive recipe_servings
     * contribute (division-by-zero guard); {@code people} must be at least 1. To-taste rows are
     * left out, as above. The factor and both
     * guards are the ones {@link Portions} defines for the recipe endpoint; keep them in step.
     */
    public List<AggregatedIngredient> aggregateIngredients(List<Integer> recipeIds, int people) {
        if (!Portions.isValidPeople(people)) {
            throw new IllegalArgumentException(Portions.INVALID_PEOPLE_MESSAGE);
        }
        if (recipeIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = recipeIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT ri.recipe_ingredient_item_id AS item_id, "
                + "ii.inventory_item_name_deutsch AS name_deutsch, "
                + "ri.recipe_ingredient_unit AS unit, "
                + "SUM(ri.recipe_ingredient_quantity_needed * ? / CAST(r.recipe_servings AS DOUBLE)) AS total_quantity, "
                + "GROUP_CONCAT(DISTINCT ri.recipe_ingredient_recipe_id) AS recipe_ids "
                + "FROM recipe_ingredient ri "
                + "JOIN inventory_item ii ON ii.inventory_item_id = ri.recipe_ingredient_item_id "
                + "JOIN recipe r ON r.recipe_id = ri.recipe_ingredient_recipe_id "
                + "WHERE ri.recipe_ingredient_recipe_id IN (" + placeholders + ") "
                + "AND r.recipe_servings > 0 "
                + "AND ri.recipe_ingredient_to_taste = FALSE "
                + "GROUP BY ri.recipe_ingredient_item_id, ri.recipe_ingredient_unit "
                + "ORDER BY ii.inventory_item_name_deutsch";

        return runAggregateQuery(sql, recipeIds, Optional.of(people));
    }

    /**
     * Same as {@link #aggregateIngredients(List, int)}, but every recipe has its own head count: the map gives,
     * per recipe id, the total number of servings planned across all its meals (a recipe planned twice, for 2 and
     * for 4, is 6). Each recipe's quantities are scaled by {@code servings / recipe_servings} before summing
     * across recipes. A recipe with a servings count of 0 or less is skipped, and to-taste rows are left out, as
     * above. An empty map gives an empty result.
     */
    public List<AggregatedIngredient> aggregateIngredientsByServings(java.util.Map<Integer, Integer> servingsPerRecipe) {
        for (int servings : servingsPerRecipe.values()) {
            if (!Portions.isValidPeople(servings)) {
                throw new IllegalArgumentException(Portions.INVALID_PEOPLE_MESSAGE);
            }
        }
        if (servingsPerRecipe.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> recipeIds = new ArrayList<>(servingsPerRecipe.keySet());
        String whens = recipeIds.stream().map(id -> "WHEN ? THEN ?").collect(Collectors.joining(" "));
        String placeholders = recipeIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT ri.recipe_ingredient_item_id AS item_id, "
                + "ii.inventory_item_name_deutsch AS name_deutsch, "
                + "ri.recipe_ingredient_unit AS unit, "
                + "SUM(ri.recipe_ingredient_quantity_needed * (CASE ri.recipe_ingredient_recipe_id " + whens
                + " END) / CAST(r.recipe_servings AS DOUBLE)) AS total_quantity, "
                + "GROUP_CONCAT(DISTINCT ri.recipe_ingredient_recipe_id) AS recipe_ids "
                + "FROM recipe_ingredient ri "
                + "JOIN inventory_item ii ON ii.inventory_item_id = ri.recipe_ingredient_item_id "
                + "JOIN recipe r ON r.recipe_id = ri.recipe_ingredient_recipe_id "
                + "WHERE ri.recipe_ingredient_recipe_id IN (" + placeholders + ") "
                + "AND r.recipe_servings > 0 "
                + "AND ri.recipe_ingredient_to_taste = FALSE "
                + "GROUP BY ri.recipe_ingredient_item_id, ri.recipe_ingredient_unit "
                + "ORDER BY ii.inventory_item_name_deutsch";

        List<Integer> params = new ArrayList<>();
        for (int recipeId : recipeIds) {
            params.add(recipeId);
            params.add(servingsPerRecipe.get(recipeId));
        }
        params.addAll(recipeIds);
        return runAggregateQuery(sql, params, "recipes " + recipeIds);
    }

    private List<AggregatedIngredient> runAggregateQuery(String sql, List<Integer> recipeIds,
                                                           Optional<Integer> people) {
        List<Integer> params = new ArrayList<>();
        people.ifPresent(params::add);
        params.addAll(recipeIds);
        return runAggregateQuery(sql, params, "recipes " + recipeIds);
    }

    // params are bound in order, all integers (head counts and recipe ids)
    private List<AggregatedIngredient> runAggregateQuery(String sql, List<Integer> params, String what) {
        List<AggregatedIngredient> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (int param : params) {
                ps.setInt(i++, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<Integer> sourceRecipeIds = Arrays.stream(rs.getString("recipe_ids").split(","))
                            .map(Integer::parseInt)
                            .collect(Collectors.toList());
                    out.add(new AggregatedIngredient(
                            rs.getInt("item_id"),
                            rs.getString("name_deutsch"),
                            rs.getDouble("total_quantity"),
                            rs.getString("unit"),
                            sourceRecipeIds));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not aggregate ingredients for " + what, e);
        }
        return out;
    }

    /**
     * Every store_item that can supply the given inventory item, with its brand and store name,
     * and its store_discount for today if one is currently running. When a store_item is linked to
     * a store_product, that product's last-seen date and availability come along too (both null
     * otherwise), so the service can tell a fresh listing from a stale one.
     */
    public List<StoreItemCandidate> loadStoreCandidates(int inventoryItemId, LocalDate today) {
        String sql = "SELECT si.store_item_id, s.store_name, b.brand_name, "
                + "si.store_item_package_unit, si.store_item_package_size, si.store_item_price, "
                + "si.store_item_deposit, si.store_item_is_available, "
                + "sp.store_product_last_seen, sp.store_product_is_available, "
                + "sd.store_discount_price, sd.store_discount_start_date, sd.store_discount_end_date, "
                + "sd.store_discount_deal_quantity, sd.store_discount_deal_price "
                + "FROM store_item si "
                + "JOIN store s ON s.store_id = si.store_item_store_id "
                + "JOIN brand b ON b.brand_id = si.store_item_brand_id "
                + "LEFT JOIN store_product sp ON sp.store_product_id = si.store_item_store_product_id "
                + "LEFT JOIN store_discount sd ON sd.store_discount_id = ("
                + "    SELECT sd2.store_discount_id FROM store_discount sd2 "
                + "    WHERE sd2.store_discount_store_item_id = si.store_item_id "
                + "      AND sd2.store_discount_start_date <= ? "
                + "      AND sd2.store_discount_end_date >= ? "
                + "    ORDER BY sd2.store_discount_id DESC LIMIT 1) "
                + "WHERE si.store_item_item_id = ?";

        List<StoreItemCandidate> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String todayText = today.toString();
            ps.setString(1, todayText);
            ps.setString(2, todayText);
            ps.setInt(3, inventoryItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapCandidate(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load store candidates for item " + inventoryItemId, e);
        }
        return out;
    }

    private StoreItemCandidate mapCandidate(ResultSet rs) throws SQLException {
        LocalDate lastSeen = rs.getObject("store_product_last_seen", LocalDate.class);
        LocalDate validFrom = rs.getObject("store_discount_start_date", LocalDate.class);
        LocalDate validUntil = rs.getObject("store_discount_end_date", LocalDate.class);
        int dealQuantity = rs.getInt("store_discount_deal_quantity");
        if (rs.wasNull()) {
            dealQuantity = 1;
        }
        return new StoreItemCandidate(
                rs.getInt("store_item_id"),
                rs.getString("store_name"),
                rs.getString("brand_name"),
                rs.getString("store_item_package_unit"),
                rs.getInt("store_item_package_size"),
                getNullableDouble(rs, "store_item_price"),
                getNullableDouble(rs, "store_item_deposit"),
                rs.getBoolean("store_item_is_available"),
                lastSeen,
                getNullableBoolean(rs, "store_product_is_available"),
                getNullableDouble(rs, "store_discount_price"),
                validFrom,
                validUntil,
                dealQuantity,
                getNullableDouble(rs, "store_discount_deal_price"));
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    /** Opens a connection with auto-commit off, for a caller that needs to combine several writes atomically. */
    public Connection openTransaction() {
        try {
            Connection con = Database.getConnection();
            con.setAutoCommit(false);
            return con;
        } catch (SQLException e) {
            throw new RuntimeException("Could not open transaction", e);
        }
    }

    /**
     * Inserts a new shopping_list row, on the caller's transaction. Returns its generated id. shoppingDate is the day of
     * the trip (offers are judged on it), fromDate the first meal day the list counts; either may be null.
     */
    public int insertShoppingList(Connection con, Integer mealPlanId, LocalDate createdDate, LocalDate shoppingDate,
                                  LocalDate fromDate, boolean status) throws SQLException {
        String sql = "INSERT INTO shopping_list (shopping_list_meal_plan_id, shopping_list_created_date, "
                + "shopping_list_shopping_date, shopping_list_from_date, shopping_list_status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, mealPlanId);
            ps.setObject(2, createdDate);
            ps.setObject(3, shoppingDate);
            ps.setObject(4, fromDate);
            ps.setBoolean(5, status);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /**
     * Inserts one shopping_list_item row, on the caller's transaction, and returns its id. unit is the unit of the
     * amount; needsReview marks a line whose stock was not subtracted because the units differ. storeItemId is nullable
     * (shopping_list_item_store_item_id): null means the line is still awaiting the user's choice
     * of store product. inventoryItemId (shopping_list_item_inventory_item_id) is nullable at the
     * table level for a purely manual free-text line, but every generated line must set it — it is
     * the only way to know which store_items are candidates for the line once it is reloaded.
     */
    public int insertShoppingListItem(Connection con, int shoppingListId, Integer storeItemId,
                                      Integer inventoryItemId, String storeSection, BigDecimal quantityNeeded,
                                      String unit, Boolean isDiscounted, boolean purchaseStatus, boolean isManual,
                                      boolean needsReview) throws SQLException {
        String sql = "INSERT INTO shopping_list_item (shopping_list_id, shopping_list_item_store_item_id, "
                + "shopping_list_item_inventory_item_id, shopping_list_item_store_section, "
                + "shopping_list_item_quantity_needed, shopping_list_item_unit, shopping_list_item_is_discounted, "
                + "shopping_list_item_purchase_status, shopping_list_item_is_manual, shopping_list_item_needs_review) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, shoppingListId);
            ps.setObject(2, storeItemId);
            ps.setObject(3, inventoryItemId);
            ps.setString(4, storeSection);
            ps.setBigDecimal(5, quantityNeeded);
            ps.setString(6, unit);
            ps.setObject(7, isDiscounted);
            ps.setBoolean(8, purchaseStatus);
            ps.setBoolean(9, isManual);
            ps.setBoolean(10, needsReview);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Records which recipes need a line, on the caller's transaction. */
    public void insertItemRecipes(Connection con, int shoppingListItemId, List<Integer> recipeIds) throws SQLException {
        String sql = "INSERT INTO shopping_list_item_recipe (shopping_list_item_recipe_item_id, "
                + "shopping_list_item_recipe_recipe_id) VALUES (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int recipeId : recipeIds) {
                ps.setInt(1, shoppingListItemId);
                ps.setInt(2, recipeId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** The shopping_list row for a persisted list, or empty if no such list exists. */
    public Optional<ShoppingListRow> findShoppingListById(int shoppingListId) {
        String sql = "SELECT shopping_list_id, shopping_list_meal_plan_id, shopping_list_created_date, "
                + "shopping_list_shopping_date, shopping_list_from_date, shopping_list_status "
                + "FROM shopping_list WHERE shopping_list_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shoppingListId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ShoppingListRow(
                        rs.getInt("shopping_list_id"),
                        (Integer) rs.getObject("shopping_list_meal_plan_id"),
                        rs.getObject("shopping_list_created_date", LocalDate.class),
                        rs.getObject("shopping_list_shopping_date", LocalDate.class),
                        rs.getObject("shopping_list_from_date", LocalDate.class),
                        rs.getBoolean("shopping_list_status")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load shopping list " + shoppingListId, e);
        }
    }

    /**
     * Every shopping_list_item on a persisted list, joined to its inventory_item's names (both
     * null for a manual line with no catalog match), ordered by insertion.
     */
    public List<ShoppingListItemRow> findItemsForShoppingList(int shoppingListId) {
        String sql = "SELECT sli.shopping_list_item_id, sli.shopping_list_item_inventory_item_id, "
                + "ii.inventory_item_name_deutsch, ii.inventory_item_name_english, "
                + "COALESCE(sli.shopping_list_item_unit, ii.inventory_item_unit) AS unit, "
                + "sli.shopping_list_item_quantity_needed, sli.shopping_list_item_store_item_id, "
                + "sli.shopping_list_item_is_manual, sli.shopping_list_item_needs_review, "
                + "sli.shopping_list_item_purchase_status, "
                + "ii.inventory_item_quantity, ii.inventory_item_unit AS stock_unit "
                + "FROM shopping_list_item sli "
                + "LEFT JOIN inventory_item ii ON ii.inventory_item_id = sli.shopping_list_item_inventory_item_id "
                + "WHERE sli.shopping_list_id = ? "
                + "ORDER BY sli.shopping_list_item_id";
        List<ShoppingListItemRow> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shoppingListId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ShoppingListItemRow(
                            rs.getInt("shopping_list_item_id"),
                            (Integer) rs.getObject("shopping_list_item_inventory_item_id"),
                            rs.getString("inventory_item_name_deutsch"),
                            rs.getString("inventory_item_name_english"),
                            rs.getString("unit"),
                            rs.getDouble("shopping_list_item_quantity_needed"),
                            (Integer) rs.getObject("shopping_list_item_store_item_id"),
                            rs.getBoolean("shopping_list_item_is_manual"),
                            rs.getBoolean("shopping_list_item_needs_review"),
                            rs.getBoolean("shopping_list_item_purchase_status"),
                            (Integer) rs.getObject("inventory_item_quantity"),
                            rs.getString("stock_unit")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load items for shopping list " + shoppingListId, e);
        }
        return out;
    }

    /** The recipes that need each line of a list (line id -> recipes, by name). */
    public Map<Integer, List<SourceRecipe>> recipesByItem(int shoppingListId) {
        String sql = "SELECT sir.shopping_list_item_recipe_item_id AS item_id, r.recipe_id, r.recipe_name "
                + "FROM shopping_list_item_recipe sir "
                + "JOIN shopping_list_item sli ON sli.shopping_list_item_id = sir.shopping_list_item_recipe_item_id "
                + "JOIN recipe r ON r.recipe_id = sir.shopping_list_item_recipe_recipe_id "
                + "WHERE sli.shopping_list_id = ? ORDER BY r.recipe_name, r.recipe_id";
        Map<Integer, List<SourceRecipe>> out = new HashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shoppingListId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getInt("item_id"), k -> new ArrayList<>())
                            .add(new SourceRecipe(rs.getInt("recipe_id"), rs.getString("recipe_name")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the recipes of shopping list " + shoppingListId, e);
        }
        return out;
    }

    /** The saved lists of a meal plan, newest first, each with its number of lines. */
    public List<ShoppingListSummary> findListsForPlan(int mealPlanId) {
        return findLists(mealPlanId);
    }

    /** Every saved list, newest first (lists that belong to no plan included). */
    public List<ShoppingListSummary> findAllLists() {
        return findLists(null);
    }

    private List<ShoppingListSummary> findLists(Integer mealPlanId) {
        String sql = "SELECT sl.shopping_list_id, sl.shopping_list_meal_plan_id, sl.shopping_list_created_date, "
                + "sl.shopping_list_shopping_date, sl.shopping_list_from_date, sl.shopping_list_status, "
                + "(SELECT COUNT(*) FROM shopping_list_item sli WHERE sli.shopping_list_id = sl.shopping_list_id) AS line_count, "
                + "(SELECT COUNT(*) FROM shopping_list_item sli WHERE sli.shopping_list_id = sl.shopping_list_id "
                + "AND sli.shopping_list_item_purchase_status) AS purchased_count "
                + "FROM shopping_list sl WHERE (? IS NULL OR sl.shopping_list_meal_plan_id = ?) "
                + "ORDER BY sl.shopping_list_id DESC";
        List<ShoppingListSummary> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, mealPlanId);
            ps.setObject(2, mealPlanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ShoppingListSummary(
                            rs.getInt("shopping_list_id"),
                            (Integer) rs.getObject("shopping_list_meal_plan_id"),
                            rs.getObject("shopping_list_created_date", LocalDate.class),
                            rs.getObject("shopping_list_shopping_date", LocalDate.class),
                            rs.getObject("shopping_list_from_date", LocalDate.class),
                            rs.getBoolean("shopping_list_status"),
                            rs.getInt("line_count"),
                            rs.getInt("purchased_count")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not list the shopping lists", e);
        }
        return out;
    }

    /**
     * The inventory_item_id a shopping_list_item is for, or empty if the item doesn't exist or
     * has no catalog link (a manual line).
     */
    public Optional<Integer> findInventoryItemIdForItem(int shoppingListItemId) {
        String sql = "SELECT shopping_list_item_inventory_item_id FROM shopping_list_item "
                + "WHERE shopping_list_item_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shoppingListItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable((Integer) rs.getObject(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load inventory item for shopping list item "
                    + shoppingListItemId, e);
        }
    }

    /** The shopping_list_id a shopping_list_item belongs to, or empty if no such item exists. */
    public Optional<Integer> findShoppingListIdForItem(int shoppingListItemId) {
        String sql = "SELECT shopping_list_id FROM shopping_list_item WHERE shopping_list_item_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shoppingListItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up shopping list for item " + shoppingListItemId, e);
        }
    }

    /** Ticks a line as bought (or unticks it). */
    public void setPurchased(int shoppingListItemId, boolean purchased) {
        update("UPDATE shopping_list_item SET shopping_list_item_purchase_status = ? WHERE shopping_list_item_id = ?",
                purchased, shoppingListItemId);
    }

    /** Changes the amount of a line (its unit stays). */
    public void setQuantityNeeded(int shoppingListItemId, BigDecimal quantity) {
        update("UPDATE shopping_list_item SET shopping_list_item_quantity_needed = ? WHERE shopping_list_item_id = ?",
                quantity, shoppingListItemId);
    }

    /** Opens or completes a list. */
    public void setListStatus(int shoppingListId, boolean completed) {
        update("UPDATE shopping_list SET shopping_list_status = ? WHERE shopping_list_id = ?", completed, shoppingListId);
    }

    /** Removes one line of a list together with the recipe links that belong to it. */
    public void deleteItem(int shoppingListItemId) {
        update("DELETE FROM shopping_list_item_recipe WHERE shopping_list_item_recipe_item_id = ?", shoppingListItemId);
        update("DELETE FROM shopping_list_item WHERE shopping_list_item_id = ?", shoppingListItemId);
    }

    private static void update(String sql, Object... params) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update the shopping list", e);
        }
    }

    /** Sets (or, with null, clears) the store_item the user chose for this line. */
    public void setChosenStoreItem(int shoppingListItemId, Integer storeItemId) {
        String sql = "UPDATE shopping_list_item SET shopping_list_item_store_item_id = ? "
                + "WHERE shopping_list_item_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, storeItemId);
            ps.setInt(2, shoppingListItemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not set chosen store item for shopping list item "
                    + shoppingListItemId, e);
        }
    }
}
