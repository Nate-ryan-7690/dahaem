package de.dahaem.mealplanner;

import de.dahaem.shared.Database;
import de.dahaem.shared.LikePattern;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All SQL for the recipe, recipe_ingredient, recipe_instructions, recipe_tag tables lives here.
 * The rest of the app never writes SQL against the database; it calls these methods.
 */

public class RecipeRepository {
    // Counts the number of recipes in the Database
    public long countAll(){
        String sql = "SELECT COUNT(*) FROM recipe";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()){
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e){
            throw new RuntimeException("Could not count recipes", e);
        }

    }

    // Finds all recipes and sorts them by name
    public List<Recipe> findAll(){
        String sql = "SELECT recipe_id, recipe_name, recipe_servings, recipe_servings_max, recipe_cook_time" +
                ", recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat" +
                ", recipe_fibre, recipe_sugar" +
                ", recipe_description, recipe_image_url, recipe_thumbnail_url, recipe_image_source" +
                ", recipe_cuisine, recipe_rating_avg, recipe_rating_count, recipe_published_date " +
                "FROM recipe ORDER BY recipe_name";

        List<Recipe> recipes = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                recipes.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load inventory items", e);
        }
        return recipes;
    }

    public List<RecipeIngredient> findAllIngredients(int recipeId){
        String sql = "SELECT ri.recipe_ingredient_item_id, ri.recipe_ingredient_recipe_id" +
                ", ri.recipe_ingredient_unit, ri.recipe_ingredient_quantity_needed, ri.recipe_ingredient_to_taste" +
                ", ii.inventory_item_name_english, ii.inventory_item_name_deutsch " +
                "FROM recipe_ingredient ri " +
                "INNER JOIN inventory_item ii ON ri.recipe_ingredient_item_id = ii.inventory_item_id " +
                "WHERE ri.recipe_ingredient_recipe_id = ?";

        List<RecipeIngredient> ingredients = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ingredients.add(mapRowIngredient(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load ingredients for recipe " + recipeId, e);
        }
        return ingredients;
    }

    public List<RecipeInstruction> findAllInstructions(int recipeId){
        String sql = "SELECT recipe_instructions_step_id, recipe_instructions_recipe_id, recipe_instructions" +
                "_step_number, recipe_instructions_instruction, recipe_instructions_notes," +
                "recipe_instructions_source " +
                "FROM recipe_instructions WHERE recipe_instructions_recipe_id = ? " +
                "ORDER BY recipe_instructions_step_number"
                ;

        List<RecipeInstruction> instruction = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    instruction.add(mapRowInstruction(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load instructions for recipe " + recipeId, e);
        }
        return instruction;
    }

    private static final String COLUMNS = "r.recipe_id, r.recipe_name, r.recipe_servings, r.recipe_servings_max"
            + ", r.recipe_cook_time, r.recipe_prep_time, r.recipe_calories, r.recipe_protein, r.recipe_carbs"
            + ", r.recipe_fat, r.recipe_fibre, r.recipe_sugar, r.recipe_description, r.recipe_image_url"
            + ", r.recipe_thumbnail_url, r.recipe_image_source, r.recipe_cuisine, r.recipe_rating_avg"
            + ", r.recipe_rating_count, r.recipe_published_date";

    private static final String TOTAL_TIME = "(r.recipe_prep_time + r.recipe_cook_time)";

    /**
     * The WHERE clause of a search; the values go into {@code params} in the order of the placeholders. Only fixed
     * text is appended here, user text always travels as a parameter.
     */
    private static String where(RecipeQuery q, List<Object> params) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        for (String word : q.nameWords()) {
            w.append(" AND r.recipe_name LIKE ?");
            params.add(LikePattern.contains(word));
        }
        for (String ingredient : q.ingredients()) {
            w.append(" AND EXISTS (SELECT 1 FROM recipe_ingredient ri"
                    + " INNER JOIN inventory_item ii ON ii.inventory_item_id = ri.recipe_ingredient_item_id"
                    + " WHERE ri.recipe_ingredient_recipe_id = r.recipe_id"
                    + " AND (ii.inventory_item_name_deutsch LIKE ? OR ii.inventory_item_name_english LIKE ?))");
            params.add(LikePattern.contains(ingredient));
            params.add(LikePattern.contains(ingredient));
        }
        // Through the tag id (index recipe_tag_by_tag); an unknown tag name gives NULL and so no recipe.
        for (String tag : q.tags()) {
            w.append(" AND r.recipe_id IN (SELECT rt.recipe_tag_recipe_id FROM recipe_tag rt"
                    + " WHERE rt.recipe_tag_id = (SELECT t.tag_id FROM tag t WHERE t.tag_name = ?))");
            params.add(tag);
        }
        if (q.hasTimeFilter()) {
            w.append(" AND ").append(TOTAL_TIME).append(" > 0");   // 0 = no time known
        }
        if (q.minTime() != null) {
            w.append(" AND ").append(TOTAL_TIME).append(" >= ?");
            params.add(q.minTime());
        }
        if (q.maxTime() != null) {
            w.append(" AND ").append(TOTAL_TIME).append(" <= ?");
            params.add(q.maxTime());
        }
        if (q.servings() != null) {
            w.append(" AND r.recipe_servings <= ? AND COALESCE(r.recipe_servings_max, r.recipe_servings) >= ?");
            params.add(q.servings());
            params.add(q.servings());
        }
        return w.toString();
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /** How many recipes match the query. */
    public long countMatching(RecipeQuery q) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM recipe r" + where(q, params);
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not count the matching recipes", e);
        }
    }

    /** One page of the recipes matching the query, in the query's (total) sort order. */
    public List<Recipe> findPage(RecipeQuery q, int limit, long offset) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM recipe r" + where(q, params)
                + " ORDER BY " + q.sort().orderBy() + " LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        List<Recipe> recipes = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recipes.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not search recipes", e);
        }
        return recipes;
    }

    /** The ids of every recipe matching the query, in the query's sort order (for paging after the diet filter). */
    public List<Integer> findMatchingIds(RecipeQuery q) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT r.recipe_id FROM recipe r" + where(q, params) + " ORDER BY " + q.sort().orderBy();
        List<Integer> ids = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not search recipes", e);
        }
        return ids;
    }

    /** The recipes with these ids, in the order of {@code ids}; an id without a recipe is skipped. */
    public List<Recipe> findByIds(List<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // Only the number of placeholders is built here; the ids are bound.
        String sql = "SELECT " + COLUMNS + " FROM recipe r WHERE r.recipe_id IN ("
                + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")";
        Map<Integer, Recipe> byId = new HashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Recipe recipe = mapRow(rs);
                    byId.put(recipe.getId(), recipe);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load recipes by id", e);
        }
        List<Recipe> ordered = new ArrayList<>();
        for (int id : ids) {
            Recipe recipe = byId.get(id);
            if (recipe != null) {
                ordered.add(recipe);
            }
        }
        return ordered;
    }

    // Searches recipes by id
    public Optional<Recipe> findById(int id){
        String sql = "SELECT recipe_id, recipe_name, recipe_servings, recipe_servings_max, recipe_cook_time" +
                ", recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat" +
                ", recipe_fibre, recipe_sugar" +
                ", recipe_description, recipe_image_url, recipe_thumbnail_url, recipe_image_source" +
                ", recipe_cuisine, recipe_rating_avg, recipe_rating_count, recipe_published_date " +
                "FROM recipe WHERE recipe_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id); // Parameterized: never build SQL by string concatenation.
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load recipe " + id, e);
        }
    }

    //Null check method to be called where needed
    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    // Turns the current ResultSet row into a Recipe. Kept in one place so every query maps the same way.
    private Recipe mapRow(ResultSet rs) throws SQLException {
        return new Recipe(
        rs.getInt("recipe_id"),
        rs.getString("recipe_name"),
        rs.getInt("recipe_servings"),
        getNullableInt(rs, "recipe_servings_max"),
        rs.getInt("recipe_cook_time"),
        rs.getInt("recipe_prep_time"),
        getNullableInt(rs,"recipe_calories"),
        getNullableInt(rs, "recipe_protein"),
        getNullableInt(rs, "recipe_carbs"),
        getNullableInt(rs, "recipe_fat"),
        getNullableInt(rs, "recipe_fibre"),
        getNullableInt(rs, "recipe_sugar"),
        rs.getString("recipe_description"),
        rs.getString("recipe_image_url"),
        rs.getString("recipe_thumbnail_url"),
        rs.getString("recipe_image_source"),
        rs.getString("recipe_cuisine"),
        rs.getBigDecimal("recipe_rating_avg"),
        getNullableInt(rs, "recipe_rating_count"),
        rs.getObject("recipe_published_date", LocalDate.class)
        );
    }
    private RecipeIngredient mapRowIngredient(ResultSet rs) throws SQLException {
        return new RecipeIngredient(
                rs.getInt("recipe_ingredient_item_id"),
                rs.getInt("recipe_ingredient_recipe_id"),
                rs.getString("recipe_ingredient_unit"),
                rs.getBigDecimal("recipe_ingredient_quantity_needed"),
                rs.getBoolean("recipe_ingredient_to_taste"),
                rs.getString("inventory_item_name_english"),
                rs.getString("inventory_item_name_deutsch")
        );
    }
    private RecipeInstruction mapRowInstruction(ResultSet rs) throws SQLException {
        return new RecipeInstruction(
                rs.getInt("recipe_instructions_step_id"),
                rs.getInt("recipe_instructions_recipe_id"),
                rs.getInt("recipe_instructions_step_number"),
                rs.getString("recipe_instructions_instruction"),
                rs.getString("recipe_instructions_notes"),
                rs.getString("recipe_instructions_source")
        );

    }
}
