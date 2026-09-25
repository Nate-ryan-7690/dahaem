package de.dahaem.suggestions;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
}
