package de.dahaem.diet;

import de.dahaem.shared.Database;
import de.dahaem.tags.CatalogItem;
import de.dahaem.tags.ItemAnimalRepository;
import de.dahaem.tags.RecipeFacts;
import de.dahaem.tags.RecipeTagRepository;
import de.dahaem.tags.TagRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** All SQL for the diet filter. Read-only: it never writes. */
public class DietFilterRepository {

    private final ItemAnimalRepository animals = new ItemAnimalRepository();
    private final RecipeTagRepository recipeTags = new RecipeTagRepository();
    private final TagRepository tags = new TagRepository();

    /** The facts of one recipe, or empty facts when it has none. */
    public RecipeDietFacts loadRecipe(int recipeId) {
        Map<Integer, RecipeDietFacts> all = load(recipeId);
        RecipeDietFacts facts = all.get(recipeId);
        return facts != null ? facts : new RecipeDietFacts(recipeId, Set.of(), List.of(), List.of());
    }

    /** The facts of every recipe, by recipe id. */
    public Map<Integer, RecipeDietFacts> loadAll() {
        return load(null);
    }

    /** tag name → group (tag_category) for every tag; the filter needs it to know which tags depend on the ingredients. */
    public Map<String, String> tagGroups() {
        Map<String, String> out = new HashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT tag_name, tag_category FROM tag");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the tag groups", e);
        }
        return out;
    }

    private Map<Integer, RecipeDietFacts> load(Integer onlyRecipe) {
        RecipeTagRepository.requireMigration0012();
        Map<Integer, CatalogItem> catalog = new HashMap<>();
        animals.findAllItems().forEach(i -> catalog.put(i.id(), i));
        Map<Integer, Map<String, String>> itemAnimals = animals.findAnimalCertainty();
        Map<Integer, List<RecipeFacts.Unmatched>> unmatched = recipeTags.loadUnmatched(onlyRecipe);
        Map<Integer, Set<String>> tagNames = onlyRecipe == null ? tagNamesOfAll() : tagNamesOf(onlyRecipe);

        Map<Integer, List<RecipeFacts.Ingredient>> ingredients = new HashMap<>();
        String sql = "SELECT recipe_ingredient_recipe_id, recipe_ingredient_item_id FROM recipe_ingredient"
                + (onlyRecipe == null ? "" : " WHERE recipe_ingredient_recipe_id = ?");
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (onlyRecipe != null) {
                ps.setInt(1, onlyRecipe);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CatalogItem item = catalog.get(rs.getInt(2));
                    ingredients.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(
                            RecipeTagRepository.ingredient(item, item == null ? Map.of()
                                    : itemAnimals.getOrDefault(item.id(), Map.of())));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the recipe ingredients", e);
        }

        Set<Integer> ids = new LinkedHashSet<>(ingredients.keySet());
        ids.addAll(unmatched.keySet());
        ids.addAll(tagNames.keySet());
        if (onlyRecipe != null) {
            ids.add(onlyRecipe);
        }
        Map<Integer, RecipeDietFacts> out = new HashMap<>();
        for (int id : ids) {
            out.put(id, new RecipeDietFacts(id, tagNames.getOrDefault(id, Set.of()),
                    ingredients.getOrDefault(id, List.of()), unmatched.getOrDefault(id, List.of())));
        }
        return out;
    }

    private Map<Integer, Set<String>> tagNamesOfAll() {
        Map<Integer, Set<String>> out = new HashMap<>();
        tags.tagNamesByRecipe().forEach((id, names) -> out.put(id, new TreeSet<>(names)));
        return out;
    }

    private Map<Integer, Set<String>> tagNamesOf(int recipeId) {
        Set<String> names = new TreeSet<>();
        tags.recipeTagsForRecipe(recipeId).forEach(t -> names.add(t.name()));
        return Map.of(recipeId, names);
    }
}
