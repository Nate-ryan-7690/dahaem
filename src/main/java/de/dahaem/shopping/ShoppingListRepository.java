package de.dahaem.shopping;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
     * so the same item can come back as more than one row if recipes disagree on unit.
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
                + "GROUP BY ri.recipe_ingredient_item_id, ri.recipe_ingredient_unit "
                + "ORDER BY ii.inventory_item_name_deutsch";

        List<AggregatedIngredient> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (int recipeId : recipeIds) {
                ps.setInt(i++, recipeId);
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
            throw new RuntimeException("Could not aggregate ingredients for recipes " + recipeIds, e);
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
                + "sd.store_discount_price, sd.store_discount_end_date, "
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
        String lastSeenText = rs.getString("store_product_last_seen");
        String validUntilText = rs.getString("store_discount_end_date");
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
                lastSeenText == null ? null : LocalDate.parse(lastSeenText),
                getNullableBoolean(rs, "store_product_is_available"),
                getNullableDouble(rs, "store_discount_price"),
                validUntilText == null ? null : LocalDate.parse(validUntilText),
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

    /** Inserts a new shopping_list row, on the caller's transaction. Returns its generated id. */
    public int insertShoppingList(Connection con, Integer mealPlanId, LocalDate createdDate, boolean status)
            throws SQLException {
        String sql = "INSERT INTO shopping_list (shopping_list_meal_plan_id, shopping_list_created_date, "
                + "shopping_list_status) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, mealPlanId);
            ps.setString(2, createdDate.toString());
            ps.setBoolean(3, status);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /**
     * Inserts one shopping_list_item row, on the caller's transaction. store_item_id is a required
     * (NOT NULL, FK to store_item) column, so this is only called for items a current store option
     * was found for.
     */
    public void insertShoppingListItem(Connection con, int shoppingListId, int storeItemId, String storeSection,
                                        int quantityNeeded, Boolean isDiscounted, boolean purchaseStatus,
                                        boolean isManual) throws SQLException {
        String sql = "INSERT INTO shopping_list_item (shopping_list_id, shopping_list_item_store_item_id, "
                + "shopping_list_item_store_section, shopping_list_item_quantity_needed, "
                + "shopping_list_item_is_discounted, shopping_list_item_purchase_status, "
                + "shopping_list_item_is_manual) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shoppingListId);
            ps.setInt(2, storeItemId);
            ps.setString(3, storeSection);
            ps.setInt(4, quantityNeeded);
            ps.setObject(5, isDiscounted);
            ps.setBoolean(6, purchaseStatus);
            ps.setBoolean(7, isManual);
            ps.executeUpdate();
        }
    }
}
