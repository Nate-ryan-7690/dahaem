package de.dahaem.mealplanner;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
        String sql = "SELECT recipe_id, recipe_name, recipe_servings, recipe_cook_time" +
                ", recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat" +
                ", recipe_fibre, recipe_sugar " +
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

    // Searches recipes by id
    public Optional<Recipe> findById(int id){
        String sql = "SELECT recipe_id, recipe_name, recipe_servings, recipe_cook_time" +
                ", recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat" +
                ", recipe_fibre, recipe_sugar " +
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
        rs.getInt("recipe_cook_time"),
        rs.getInt("recipe_prep_time"),
        getNullableInt(rs,"recipe_calories"),
        getNullableInt(rs, "recipe_protein"),
        getNullableInt(rs, "recipe_carbs"),
        getNullableInt(rs, "recipe_fat"),
        getNullableInt(rs, "recipe_fibre"),
        getNullableInt(rs, "recipe_sugar")
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
