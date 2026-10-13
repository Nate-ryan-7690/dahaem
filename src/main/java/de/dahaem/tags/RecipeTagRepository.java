package de.dahaem.tags;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** All SQL for the derived-tag run: reads the recipes and their tags, and writes derived recipe_tag rows. */
public class RecipeTagRepository {

    public static final String SOURCE_DERIVED = "derived";

    /**
     * Where the cuisine lives once migration 0010 is in. The first name that exists wins; these are fixed
     * names that go into the SQL text, never user input.
     */
    static final List<String> CUISINE_COLUMNS = List.of("recipe_cuisine", "cuisine");

    /** One recipe_tag row as stored. */
    public record StoredTag(int tagId, String name, String category, String source) {
    }

    /** One change to apply: tag a recipe as derived, or remove a derived tag. */
    public record Change(int recipeId, String tagName, String tagCategory, boolean add) {
    }

    /** The recipe table's cuisine column, if it has one yet. */
    public Optional<String> cuisineColumn() {
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'recipe' AND COLUMN_NAME = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (String column : CUISINE_COLUMNS) {
                ps.setString(1, column);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(column);
                    }
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Could not look for the cuisine column", e);
        }
    }

    /** Every recipe with its ingredients (and their items and stored animals), by recipe id. Read-only. */
    public List<RecipeFacts> loadRecipes(Optional<String> cuisineColumn) {
        Map<Integer, Set<String>> animals = new ItemAnimalRepository().findAnimals();
        Map<Integer, List<RecipeFacts.Ingredient>> ingredients = new HashMap<>();
        String ingredientSql = "SELECT ri.recipe_ingredient_recipe_id, ii.inventory_item_id, "
                + ItemAnimalRepository.NAME_SQL.replace("inventory_item_", "ii.inventory_item_")
                + " AS item_name, ii.inventory_item_category, ii.inventory_item_subcategory "
                + "FROM recipe_ingredient ri LEFT JOIN inventory_item ii "
                + "ON ii.inventory_item_id = ri.recipe_ingredient_item_id";
        List<RecipeFacts> recipes = new ArrayList<>();
        String recipeSql = "SELECT recipe_id, recipe_name, recipe_prep_time, recipe_cook_time, recipe_calories, "
                + "recipe_protein, " + cuisineColumn.map(c -> c).orElse("NULL") + " AS cuisine "
                + "FROM recipe ORDER BY recipe_id";
        try (Connection con = Database.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(ingredientSql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt(2);
                    CatalogItem item = rs.wasNull() ? null
                            : new CatalogItem(itemId, rs.getString(3), rs.getString(4), rs.getString(5));
                    ingredients.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                            .add(new RecipeFacts.Ingredient(item, item == null ? Set.of()
                                    : animals.getOrDefault(item.id(), Set.of())));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(recipeSql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    recipes.add(new RecipeFacts(id, rs.getString(2), rs.getInt(3), rs.getInt(4),
                            (Integer) rs.getObject(5), (Integer) rs.getObject(6), rs.getString(7),
                            ingredients.getOrDefault(id, List.of())));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the recipes", e);
        }
        return recipes;
    }

    /** recipe id → the tags it has now (manual and derived). Read-only. */
    public Map<Integer, List<StoredTag>> loadStoredTags() {
        String sql = "SELECT rt.recipe_tag_recipe_id, t.tag_id, t.tag_name, t.tag_category, rt.recipe_tag_source "
                + "FROM recipe_tag rt INNER JOIN tag t ON t.tag_id = rt.recipe_tag_id "
                + "ORDER BY rt.recipe_tag_recipe_id, t.tag_name";
        Map<Integer, List<StoredTag>> out = new LinkedHashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                        .add(new StoredTag(rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the recipe tags", e);
        }
        return out;
    }

    /**
     * Applies the changes in ONE transaction: a tag is created in {@code tag} when it does not exist, then
     * linked with source 'derived'; a removal deletes only a 'derived' link. Any failure rolls everything back.
     */
    public void apply(List<Change> changes) {
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try {
                Map<String, Integer> tagIds = new HashMap<>();
                for (Change change : changes) {
                    int tagId = change.add()
                            ? tagIds.computeIfAbsent(change.tagName(), n -> findOrCreateTag(con, n, change.tagCategory()))
                            : tagIds.computeIfAbsent(change.tagName(), n -> findTag(con, n));
                    if (change.add()) {
                        insertDerived(con, change.recipeId(), tagId);
                    } else {
                        deleteDerived(con, change.recipeId(), tagId);
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not write the derived tags, nothing was changed: " + e.getMessage(), e);
        }
    }

    private static int findTag(Connection con, String name) {
        try (PreparedStatement ps = con.prepareStatement("SELECT tag_id FROM tag WHERE tag_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up tag " + name, e);
        }
        throw new IllegalStateException("tag '" + name + "' does not exist");
    }

    private static int findOrCreateTag(Connection con, String name, String category) {
        try {
            return findTag(con, name);
        } catch (IllegalStateException missing) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO tag (tag_name, tag_category) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, category);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getInt(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Could not create tag " + name, e);
            }
        }
    }

    private static void insertDerived(Connection con, int recipeId, int tagId) {
        String sql = "INSERT INTO recipe_tag (recipe_tag_recipe_id, recipe_tag_id, recipe_tag_source) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            ps.setInt(2, tagId);
            ps.setString(3, SOURCE_DERIVED);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not tag recipe " + recipeId + " with tag " + tagId, e);
        }
    }

    private static void deleteDerived(Connection con, int recipeId, int tagId) {
        String sql = "DELETE FROM recipe_tag WHERE recipe_tag_recipe_id = ? AND recipe_tag_id = ? "
                + "AND recipe_tag_source = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            ps.setInt(2, tagId);
            ps.setString(3, SOURCE_DERIVED);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not untag recipe " + recipeId + " from tag " + tagId, e);
        }
    }
}
