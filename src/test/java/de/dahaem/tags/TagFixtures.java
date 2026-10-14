package de.dahaem.tags;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Made-up catalogue and recipe rows for the tag tests (parameterized SQL, throwaway database only). */
public final class TagFixtures {

    private TagFixtures() {
    }

    public static int item(String name, String category, String subcategory) {
        String sql = "INSERT INTO inventory_item (inventory_item_name_english, inventory_item_name_deutsch, "
                + "inventory_item_unit, inventory_item_category, inventory_item_subcategory) VALUES (?, ?, 'g', ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name + " (en)");
            ps.setString(2, name);
            ps.setString(3, category);
            ps.setString(4, subcategory);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void animal(int itemId, String animal) {
        run("INSERT INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal) VALUES (?, ?)",
                itemId, animal);
    }

    public static void animalWithCertainty(int itemId, String animal, String certainty) {
        run("INSERT INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, "
                + "inventory_item_animal_certainty) VALUES (?, ?, ?)", itemId, animal, certainty);
    }

    public static void alcohol(int itemId) {
        run("UPDATE inventory_item SET inventory_item_alcohol = TRUE WHERE inventory_item_id = ?", itemId);
    }

    public static void unmatched(int recipeId, Integer lineNo, String raw, String reason) {
        run("INSERT INTO recipe_unmatched_ingredient (recipe_unmatched_ingredient_recipe_id, "
                + "recipe_unmatched_ingredient_line_no, recipe_unmatched_ingredient_raw_text, "
                + "recipe_unmatched_ingredient_reason) VALUES (?, ?, ?, ?)", recipeId, lineNo, raw, reason);
    }

    public static int recipe(String name, int prep, int cook, Integer kcal, Integer protein) {
        String sql = "INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, "
                + "recipe_calories, recipe_protein) VALUES (?, 2, ?, ?, ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, cook);
            ps.setInt(3, prep);
            ps.setObject(4, kcal);
            ps.setObject(5, protein);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void ingredient(int recipeId, int itemId) {
        run("INSERT INTO recipe_ingredient (recipe_ingredient_item_id, recipe_ingredient_recipe_id, "
                + "recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES (?, ?, 'g', 100)", itemId, recipeId);
    }

    /** Adds a tag to a recipe with the given source, creating the tag if needed. */
    public static void tag(int recipeId, String tagName, String category, String source) {
        run("INSERT IGNORE INTO tag (tag_name, tag_category) VALUES (?, ?)", tagName, category);
        run("INSERT INTO recipe_tag (recipe_tag_recipe_id, recipe_tag_id, recipe_tag_source) "
                + "SELECT ?, tag_id, ? FROM tag WHERE tag_name = ?", recipeId, source, tagName);
    }

    public static java.util.List<String> tags(int recipeId, String source) {
        String sql = "SELECT t.tag_name FROM recipe_tag rt JOIN tag t ON t.tag_id = rt.recipe_tag_id "
                + "WHERE rt.recipe_tag_recipe_id = ? AND rt.recipe_tag_source = ? ORDER BY t.tag_name";
        java.util.List<String> out = new java.util.ArrayList<>();
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            ps.setString(2, source);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public static int count(String sql) {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void run(String sql, Object... params) {
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
