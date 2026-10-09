package de.dahaem.mealplanner;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the meal_plan and meal_plan_recipe tables lives here.
 * The rest of the app never writes SQL against the database; it calls these methods.
 */

public class MealPlanRepository {

    // Creates a new meal plan for the given week and returns its generated id.
    public int create(LocalDate weekStartDate) {
        String sql = "INSERT INTO meal_plan (meal_plan_week_start_date) VALUES (?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, weekStartDate);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not create meal plan for " + weekStartDate, e);
        }
    }

    // Adds a recipe to a plan at the given meal time.
    public void addRecipe(int planId, int recipeId, String mealTime) {
        String sql = "INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, " +
                "meal_plan_recipe_meal_time) VALUES (?, ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            ps.setInt(2, planId);
            ps.setString(3, mealTime);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not add recipe " + recipeId + " to plan " + planId, e);
        }
    }

    /** Removes a recipe from a plan. Returns true if a row was actually deleted. */
    public boolean removeRecipe(int planId, int recipeId) {
        String sql = "DELETE FROM meal_plan_recipe WHERE meal_plan_recipe_plan_id = ? " +
                "AND meal_plan_recipe_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, planId);
            ps.setInt(2, recipeId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not remove recipe " + recipeId + " from plan " + planId, e);
        }
    }

    // Finds all meal plans, sorted by week start date.
    public List<MealPlan> findAll() {
        String sql = "SELECT meal_plan_id, meal_plan_week_start_date FROM meal_plan " +
                "ORDER BY meal_plan_week_start_date";

        List<MealPlan> plans = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                plans.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load meal plans", e);
        }
        return plans;
    }

    // Finds all recipes on a plan, joined to the recipe's name.
    public List<MealPlanRecipe> findRecipesForPlan(int planId) {
        String sql = "SELECT mpr.meal_plan_recipe_id, mpr.meal_plan_recipe_plan_id, " +
                "mpr.meal_plan_recipe_meal_time, r.recipe_name " +
                "FROM meal_plan_recipe mpr " +
                "INNER JOIN recipe r ON mpr.meal_plan_recipe_id = r.recipe_id " +
                "WHERE mpr.meal_plan_recipe_plan_id = ?";

        List<MealPlanRecipe> recipes = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recipes.add(mapRowRecipe(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load recipes for plan " + planId, e);
        }
        return recipes;
    }

    // Turns the current ResultSet row into a MealPlan. Kept in one place so every query maps the same way.
    private MealPlan mapRow(ResultSet rs) throws SQLException {
        return new MealPlan(
                rs.getInt("meal_plan_id"),
                rs.getObject("meal_plan_week_start_date", LocalDate.class)
        );
    }

    private MealPlanRecipe mapRowRecipe(ResultSet rs) throws SQLException {
        return new MealPlanRecipe(
                rs.getInt("meal_plan_recipe_id"),
                rs.getInt("meal_plan_recipe_plan_id"),
                rs.getString("meal_plan_recipe_meal_time"),
                rs.getString("recipe_name")
        );
    }
}
