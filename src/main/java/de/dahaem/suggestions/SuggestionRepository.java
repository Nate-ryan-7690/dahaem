package de.dahaem.suggestions;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All SQL for ranking recipes by matching ingredients lives here and nowhere else.
 * The rest of the app never writes SQL against recipe/recipe_ingredient/inventory_item/
 * store_item/store_discount for suggestions; it calls these methods.
 */
public class SuggestionRepository {

    /** Recipes ranked by how many ingredients are inventory items expiring on or before `limit`. */
    public List<RecipeSuggestion> findRankedByExpiring(LocalDate limit) {
        String sql = "SELECT DISTINCT r.recipe_id, r.recipe_name, ri.recipe_ingredient_item_id " +
                "FROM recipe r " +
                "JOIN recipe_ingredient ri ON ri.recipe_ingredient_recipe_id = r.recipe_id " +
                "JOIN inventory_item ii ON ii.inventory_item_id = ri.recipe_ingredient_item_id " +
                "WHERE ii.inventory_item_earliest_exp_date IS NOT NULL " +
                "AND ii.inventory_item_earliest_exp_date <= ? " +
                "ORDER BY r.recipe_id";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, limit.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rank(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not rank recipes by expiring inventory", e);
        }
    }

    /** Recipes ranked by how many ingredients map to a store_item with a discount current on `today`. */
    public List<RecipeSuggestion> findRankedByDiscount(LocalDate today) {
        String sql = "SELECT DISTINCT r.recipe_id, r.recipe_name, ri.recipe_ingredient_item_id " +
                "FROM recipe r " +
                "JOIN recipe_ingredient ri ON ri.recipe_ingredient_recipe_id = r.recipe_id " +
                "JOIN store_item si ON si.store_item_item_id = ri.recipe_ingredient_item_id " +
                "JOIN store_discount sd ON sd.store_discount_store_item_id = si.store_item_id " +
                "WHERE sd.store_discount_start_date <= ? AND sd.store_discount_end_date >= ? " +
                "ORDER BY r.recipe_id";
        String todayText = today.toString();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, todayText);
            ps.setString(2, todayText);
            try (ResultSet rs = ps.executeQuery()) {
                return rank(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not rank recipes by discounted ingredients", e);
        }
    }

    // Groups rows of (recipe_id, recipe_name, matching item_id) into one RecipeSuggestion per
    // recipe and orders them by match count descending, then recipe name. Recipes with zero
    // matching rows never appear here at all, since both queries are inner joins.
    private List<RecipeSuggestion> rank(ResultSet rs) throws SQLException {
        Map<Integer, String> namesById = new LinkedHashMap<>();
        Map<Integer, List<Integer>> itemIdsByRecipe = new LinkedHashMap<>();
        while (rs.next()) {
            int recipeId = rs.getInt("recipe_id");
            namesById.putIfAbsent(recipeId, rs.getString("recipe_name"));
            itemIdsByRecipe.computeIfAbsent(recipeId, id -> new ArrayList<>())
                    .add(rs.getInt("recipe_ingredient_item_id"));
        }

        List<RecipeSuggestion> suggestions = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : itemIdsByRecipe.entrySet()) {
            int recipeId = entry.getKey();
            List<Integer> itemIds = entry.getValue();
            suggestions.add(new RecipeSuggestion(recipeId, namesById.get(recipeId), itemIds.size(), itemIds));
        }
        suggestions.sort(Comparator.comparingInt(RecipeSuggestion::getScore).reversed()
                .thenComparing(RecipeSuggestion::getRecipeName));
        return suggestions;
    }

    // ------------------------------------------------------------------ pantry-first suggestions

    /**
     * For every recipe: how many ingredients it needs, how many are in stock, how many of those expire on or before
     * {@code expiryLimit}, how many of the missing ones are on sale on {@code today}, and how many of them at the
     * store named {@code store} (null = no store asked, counted as 0). "To taste" ingredients are left out. Only
     * counts come back (about one small row per recipe), so the ranking never loads 100,000 ingredient rows.
     */
    public List<PantryCoverage> coverageByRecipe(LocalDate expiryLimit, LocalDate today, String store) {
        // Usable stock: some is there and it has not expired (no date = unknown, counted as usable). Stock whose
        // date has passed is NOT usable: the ingredient counts as still to buy (shown as EXPIRED, see pantryLines).
        String usable = "(ii.inventory_item_quantity > 0 AND (ii.inventory_item_earliest_exp_date IS NULL " +
                "OR ii.inventory_item_earliest_exp_date >= ?))";
        String sql = "SELECT ri.recipe_ingredient_recipe_id AS recipe_id, COUNT(*) AS needed, " +
                "SUM(" + usable + ") AS in_stock, " +
                "SUM(" + usable + " AND ii.inventory_item_earliest_exp_date IS NOT NULL " +
                "    AND ii.inventory_item_earliest_exp_date <= ?) AS expiring, " +
                "SUM(NOT " + usable + " AND sale.item_id IS NOT NULL) AS missing_on_sale, " +
                "SUM(NOT " + usable + " AND store_sale.item_id IS NOT NULL) AS missing_at_store " +
                "FROM recipe_ingredient ri " +
                "JOIN inventory_item ii ON ii.inventory_item_id = ri.recipe_ingredient_item_id " +
                "LEFT JOIN (SELECT DISTINCT si.store_item_item_id AS item_id " +
                "           FROM store_item si " +
                "           JOIN store_discount sd ON sd.store_discount_store_item_id = si.store_item_id " +
                "           WHERE sd.store_discount_start_date <= ? AND sd.store_discount_end_date >= ?) sale " +
                "  ON sale.item_id = ii.inventory_item_id " +
                "LEFT JOIN (SELECT DISTINCT si.store_item_item_id AS item_id " +
                "           FROM store_item si " +
                "           JOIN store s ON s.store_id = si.store_item_store_id " +
                "           JOIN store_discount sd ON sd.store_discount_store_item_id = si.store_item_id " +
                "           WHERE s.store_name = ? AND sd.store_discount_start_date <= ? " +
                "             AND sd.store_discount_end_date >= ?) store_sale " +
                "  ON store_sale.item_id = ii.inventory_item_id " +
                "WHERE ri.recipe_ingredient_to_taste = 0 " +
                "GROUP BY ri.recipe_ingredient_recipe_id";
        List<PantryCoverage> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            // placeholders in the order they appear: in_stock, expiring (usable, then limit), the two missing sums,
            // the sale join (start, end), the store sale join (store, start, end)
            String todayText = today.toString();
            ps.setString(1, todayText);
            ps.setString(2, todayText);
            ps.setString(3, expiryLimit.toString());
            ps.setString(4, todayText);
            ps.setString(5, todayText);
            ps.setString(6, todayText);
            ps.setString(7, todayText);
            ps.setString(8, store == null ? "" : store);   // "" matches no store: nothing is counted at a store
            ps.setString(9, todayText);
            ps.setString(10, todayText);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PantryCoverage(rs.getInt("recipe_id"), rs.getInt("needed"), rs.getInt("in_stock"),
                            rs.getInt("expiring"), rs.getInt("missing_on_sale"), rs.getInt("missing_at_store")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not read the pantry coverage of the recipes", e);
        }
        return out;
    }

    /**
     * The ingredients of these recipes with the stock of each (recipe id -> lines, by name). A line is IN_STOCK when the
     * inventory quantity is above zero, EXPIRING when it also expires on or before {@code expiryLimit}, EXPIRED when
     * some is there but its date is before {@code today} (not counted as usable stock), TO_TASTE for "nach
     * Geschmack", otherwise MISSING. Offers are not filled in here (see {@link #offersOnSale}).
     */
    public Map<Integer, List<PantryLine>> pantryLines(Collection<Integer> recipeIds, LocalDate expiryLimit,
                                                      LocalDate today) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }
        // Only the number of placeholders is built here; the ids are bound.
        String sql = "SELECT ri.recipe_ingredient_recipe_id AS recipe_id, ii.inventory_item_id, " +
                "ii.inventory_item_name_deutsch, ii.inventory_item_name_english, " +
                "ri.recipe_ingredient_quantity_needed, ri.recipe_ingredient_unit, ri.recipe_ingredient_to_taste, " +
                "ii.inventory_item_quantity, ii.inventory_item_unit, ii.inventory_item_earliest_exp_date " +
                "FROM recipe_ingredient ri " +
                "JOIN inventory_item ii ON ii.inventory_item_id = ri.recipe_ingredient_item_id " +
                "WHERE ri.recipe_ingredient_recipe_id IN (" + placeholders(recipeIds.size()) + ") " +
                "ORDER BY ri.recipe_ingredient_recipe_id, " +
                "COALESCE(ii.inventory_item_name_deutsch, ii.inventory_item_name_english), ii.inventory_item_id";
        Map<Integer, List<PantryLine>> out = new HashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (int id : recipeIds) {
                ps.setInt(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int quantity = rs.getInt("inventory_item_quantity");
                    String expiresText = rs.getString("inventory_item_earliest_exp_date");
                    LocalDate expires = expiresText == null ? null : LocalDate.parse(expiresText);
                    PantryLine.Status status;
                    if (rs.getBoolean("recipe_ingredient_to_taste")) {
                        status = PantryLine.Status.TO_TASTE;
                    } else if (quantity > 0 && expires != null && expires.isBefore(today)) {
                        status = PantryLine.Status.EXPIRED;
                    } else if (quantity > 0) {
                        status = expires != null && !expires.isAfter(expiryLimit)
                                ? PantryLine.Status.EXPIRING : PantryLine.Status.IN_STOCK;
                    } else {
                        status = PantryLine.Status.MISSING;
                    }
                    java.math.BigDecimal needed = rs.getBigDecimal("recipe_ingredient_quantity_needed");
                    out.computeIfAbsent(rs.getInt("recipe_id"), k -> new ArrayList<>()).add(new PantryLine(
                            rs.getInt("inventory_item_id"), rs.getString("inventory_item_name_deutsch"),
                            rs.getString("inventory_item_name_english"), status,
                            needed == null ? null : needed.doubleValue(), rs.getString("recipe_ingredient_unit"),
                            quantity, rs.getString("inventory_item_unit"), expires, List.of()));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not read the ingredients of the suggested recipes", e);
        }
        return out;
    }

    /**
     * The offers that are on sale on {@code today} for these inventory items (item id -> offers), by store name,
     * then pack size. Identical rows (same store, pack, prices and end date) are returned once.
     */
    public Map<Integer, List<PantryOffer>> offersOnSale(Collection<Integer> itemIds, LocalDate today) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT DISTINCT si.store_item_item_id AS item_id, s.store_name, " +
                "si.store_item_package_size, si.store_item_package_unit, sd.store_discount_price, " +
                "si.store_item_price, sd.store_discount_end_date " +
                "FROM store_discount sd " +
                "JOIN store_item si ON si.store_item_id = sd.store_discount_store_item_id " +
                "JOIN store s ON s.store_id = si.store_item_store_id " +
                "WHERE si.store_item_item_id IN (" + placeholders(itemIds.size()) + ") " +
                "AND sd.store_discount_start_date <= ? AND sd.store_discount_end_date >= ? " +
                "ORDER BY s.store_name, si.store_item_package_size, sd.store_discount_price";
        Map<Integer, List<PantryOffer>> out = new HashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (int id : itemIds) {
                ps.setInt(i++, id);
            }
            ps.setString(i++, today.toString());
            ps.setString(i, today.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.math.BigDecimal regular = rs.getBigDecimal("store_item_price");
                    out.computeIfAbsent(rs.getInt("item_id"), k -> new ArrayList<>()).add(new PantryOffer(
                            rs.getString("store_name"), rs.getInt("store_item_package_size"),
                            rs.getString("store_item_package_unit"), rs.getBigDecimal("store_discount_price").doubleValue(),
                            regular == null ? null : regular.doubleValue(),
                            LocalDate.parse(rs.getString("store_discount_end_date"))));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not read the offers for the ingredients", e);
        }
        return out;
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }
}
