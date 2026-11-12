package de.dahaem.mealplanner;

import java.util.List;

/**
 * Business logic for recipes. No SQl here, no UI here
 * It asks the repository for data and answers questions about it.
 * This is the class future web controllers will call.
 */

public class RecipeService {
    private final RecipeRepository repository;

    public RecipeService(RecipeRepository repository){this.repository = repository; }

    public long totalRecipeCount() {return repository.countAll(); }

    public List<Recipe> allRecipes(){return repository.findAll(); }

    /** One recipe by id, or empty when there is none (a single-row query, not the whole table). */
    public java.util.Optional<Recipe> findById(int id){return repository.findById(id); }

    public List<RecipeIngredient> findAllIngredients(int recipeId)
        {return repository.findAllIngredients(recipeId);}

    public List<RecipeInstruction> findAllInstructions(int recipeId)
        {return repository.findAllInstructions(recipeId);}

    /** The household's permanent preferred servings of a recipe, or empty if it has none. */
    public java.util.Optional<Integer> preferredServings(int householdId, int recipeId) {
        return repository.findPreferredServings(householdId, recipeId);
    }

    /**
     * Sets the household's permanent preferred servings of a recipe; null clears it. Returns false when there is no
     * such recipe.
     *
     * @throws IllegalArgumentException if servings is below 1
     */
    public boolean setPreferredServings(int householdId, int recipeId, Integer servings) {
        if (servings != null && servings < 1) {
            throw new IllegalArgumentException("'servings' must be at least 1 (or null to clear it)");
        }
        return repository.setPreferredServings(householdId, recipeId, servings);
    }

}



