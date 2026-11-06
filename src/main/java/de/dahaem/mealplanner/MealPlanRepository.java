package de.dahaem.mealplanner;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    // ---------- planned meals: one row of meal_plan_recipe each, addressed by its own entry id ----------

    private static final String MEAL_SELECT =
            "SELECT mpr.meal_plan_recipe_entry_id, mpr.meal_plan_recipe_plan_id, mpr.meal_plan_recipe_id, " +
            "r.recipe_name, r.recipe_thumbnail_url, " +
            // a row made by the old endpoint has no date: it lands on its week's Monday
            "COALESCE(mpr.meal_plan_recipe_date, mp.meal_plan_week_start_date) AS meal_date, " +
            "mpr.meal_plan_recipe_meal_time, mpr.meal_plan_recipe_servings, r.recipe_preferred_servings, " +
            "mpr.meal_plan_recipe_position, mpr.meal_plan_recipe_note " +
            "FROM meal_plan_recipe mpr " +
            "INNER JOIN recipe r ON mpr.meal_plan_recipe_id = r.recipe_id " +
            "INNER JOIN meal_plan mp ON mpr.meal_plan_recipe_plan_id = mp.meal_plan_id ";

    /** The plan of the week that starts on this Monday, if there is one. */
    public Optional<MealPlan> findPlanByWeekStart(LocalDate weekStart) {
        String sql = "SELECT meal_plan_id, meal_plan_week_start_date FROM meal_plan " +
                "WHERE meal_plan_week_start_date = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, weekStart);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up the meal plan of the week of " + weekStart, e);
        }
    }

    /** Every meal of a plan, ordered by day, then position, then entry id. */
    public List<PlannedMeal> findMeals(int planId) {
        String sql = MEAL_SELECT + "WHERE mpr.meal_plan_recipe_plan_id = ? " +
                "ORDER BY meal_date, mpr.meal_plan_recipe_position, mpr.meal_plan_recipe_entry_id";
        List<PlannedMeal> meals = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    meals.add(mapMeal(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the meals of plan " + planId, e);
        }
        return meals;
    }

    public Optional<PlannedMeal> findMeal(int entryId) {
        String sql = MEAL_SELECT + "WHERE mpr.meal_plan_recipe_entry_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapMeal(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load planned meal " + entryId, e);
        }
    }

    /**
     * Meals on a day before {@code today} that still have no number of their own (they follow the default), oldest
     * entry first. These are the ones the nightly job freezes.
     */
    public List<PlannedMeal> findUnfrozenMealsBefore(LocalDate today) {
        String sql = MEAL_SELECT + "WHERE mpr.meal_plan_recipe_servings IS NULL "
                + "AND COALESCE(mpr.meal_plan_recipe_date, mp.meal_plan_week_start_date) < ? "
                + "ORDER BY mpr.meal_plan_recipe_entry_id";
        List<PlannedMeal> meals = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, today);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    meals.add(mapMeal(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the meals before " + today + " that follow the default", e);
        }
        return meals;
    }

    /**
     * Writes a meal's own number only while it still has none, so a number set by hand in the meantime is never
     * overwritten. Returns true if the row was changed.
     */
    public boolean freezeServings(int entryId, int servings) {
        String sql = "UPDATE meal_plan_recipe SET meal_plan_recipe_servings = ? "
                + "WHERE meal_plan_recipe_entry_id = ? AND meal_plan_recipe_servings IS NULL";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, servings);
            ps.setInt(2, entryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not freeze the servings of planned meal " + entryId, e);
        }
    }

    /** The next free position on a day of a plan (0 for an empty day). */
    public int nextPosition(int planId, LocalDate date) {
        String sql = "SELECT COALESCE(MAX(meal_plan_recipe_position), -1) + 1 FROM meal_plan_recipe " +
                "WHERE meal_plan_recipe_plan_id = ? AND meal_plan_recipe_date = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, planId);
            ps.setObject(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not find the next position on " + date + " of plan " + planId, e);
        }
    }

    /** Adds one planned meal and returns its entry id. servings, label and note may be null. */
    public int insertMeal(int planId, int recipeId, LocalDate date, String label, Integer servings,
                          int position, String note) {
        String sql = "INSERT INTO meal_plan_recipe (meal_plan_recipe_id, meal_plan_recipe_plan_id, " +
                "meal_plan_recipe_date, meal_plan_recipe_meal_time, meal_plan_recipe_servings, " +
                "meal_plan_recipe_position, meal_plan_recipe_note) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, recipeId);
            ps.setInt(2, planId);
            ps.setObject(3, date);
            ps.setString(4, label);
            setNullableInt(ps, 5, servings);
            ps.setInt(6, position);
            ps.setString(7, note);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not plan recipe " + recipeId + " on " + date, e);
        }
    }

    /** Writes every editable column of one meal. Returns false when there is no such meal. */
    public boolean updateMeal(int entryId, int planId, int recipeId, LocalDate date, String label,
                              Integer servings, int position, String note) {
        String sql = "UPDATE meal_plan_recipe SET meal_plan_recipe_plan_id = ?, meal_plan_recipe_id = ?, " +
                "meal_plan_recipe_date = ?, meal_plan_recipe_meal_time = ?, meal_plan_recipe_servings = ?, " +
                "meal_plan_recipe_position = ?, meal_plan_recipe_note = ? WHERE meal_plan_recipe_entry_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, planId);
            ps.setInt(2, recipeId);
            ps.setObject(3, date);
            ps.setString(4, label);
            setNullableInt(ps, 5, servings);
            ps.setInt(6, position);
            ps.setString(7, note);
            ps.setInt(8, entryId);
            // Count matched rows, not changed rows: saving the same values again must not look like "not found".
            return ps.executeUpdate() > 0 || findMeal(entryId).isPresent();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update planned meal " + entryId, e);
        }
    }

    /** Removes one planned meal by its own id. Returns true if a row was deleted. */
    public boolean deleteMeal(int entryId) {
        String sql = "DELETE FROM meal_plan_recipe WHERE meal_plan_recipe_entry_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, entryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not remove planned meal " + entryId, e);
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private PlannedMeal mapMeal(ResultSet rs) throws SQLException {
        return new PlannedMeal(
                rs.getInt("meal_plan_recipe_entry_id"),
                rs.getInt("meal_plan_recipe_plan_id"),
                rs.getInt("meal_plan_recipe_id"),
                rs.getString("recipe_name"),
                rs.getString("recipe_thumbnail_url"),
                rs.getObject("meal_date", LocalDate.class),
                rs.getString("meal_plan_recipe_meal_time"),
                (Integer) rs.getObject("meal_plan_recipe_servings"),
                (Integer) rs.getObject("recipe_preferred_servings"),
                rs.getInt("meal_plan_recipe_position"),
                rs.getString("meal_plan_recipe_note")
        );
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
