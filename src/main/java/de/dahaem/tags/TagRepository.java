package de.dahaem.tags;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All SQL for the tag and recipe_tag tables lives here.
 * The rest of the app never writes SQL against the database; it calls these methods.
 */

public class TagRepository {

    // Returns the tag with this name if one exists, otherwise creates it with the given category.
    public Tag findOrCreateTag(String name, String category){
        Optional<Tag> existing = findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }

        String sql = "INSERT INTO tag (tag_name, tag_category) VALUES (?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Tag(keys.getInt(1), name, category);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not create tag " + name, e);
        }
    }

    // Searches tags by name (tag_name is unique)
    public Optional<Tag> findByName(String name){
        String sql = "SELECT tag_id, tag_name, tag_category FROM tag WHERE tag_name = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up tag " + name, e);
        }
    }

    // Links a recipe to a tag. Safe to call more than once for the same pair.
    public void tagRecipe(int recipeId, int tagId){
        String sql = "INSERT IGNORE INTO recipe_tag (recipe_tag_recipe_id, recipe_tag_id) VALUES (?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            ps.setInt(2, tagId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not tag recipe " + recipeId + " with tag " + tagId, e);
        }
    }

    // Finds all tags attached to a recipe, sorted by name
    public List<Tag> tagsForRecipe(int recipeId){
        String sql = "SELECT t.tag_id, t.tag_name, t.tag_category " +
                "FROM tag t " +
                "INNER JOIN recipe_tag rt ON rt.recipe_tag_id = t.tag_id " +
                "WHERE rt.recipe_tag_recipe_id = ? " +
                "ORDER BY t.tag_name";

        List<Tag> tags = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load tags for recipe " + recipeId, e);
        }
        return tags;
    }

    // Finds the ids of every recipe carrying this tag
    public List<Integer> recipesForTag(int tagId){
        String sql = "SELECT recipe_tag_recipe_id FROM recipe_tag WHERE recipe_tag_id = ?";

        List<Integer> recipeIds = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recipeIds.add(rs.getInt("recipe_tag_recipe_id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load recipes for tag " + tagId, e);
        }
        return recipeIds;
    }

    // Finds every tag, sorted by name
    public List<Tag> allTags(){
        String sql = "SELECT tag_id, tag_name, tag_category FROM tag ORDER BY tag_name";

        List<Tag> tags = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tags.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load tags", e);
        }
        return tags;
    }

    // Turns the current ResultSet row into a Tag. Kept in one place so every query maps the same way.
    private Tag mapRow(ResultSet rs) throws SQLException {
        return new Tag(
                rs.getInt("tag_id"),
                rs.getString("tag_name"),
                rs.getString("tag_category")
        );
    }
}
