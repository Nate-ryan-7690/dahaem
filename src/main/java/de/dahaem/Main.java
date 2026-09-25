package de.dahaem;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.InventoryService;
import de.dahaem.inventory.Item;
import de.dahaem.mealplanner.*;

import java.util.List;

/**
 * Temporary console runner. This is how we test the engine before there is a web UI.
 * Later, a web controller replaces this file as the entry point; the services below stay the same.
 */
public class Main {
    public static void main(String[] args) {
        InventoryRepository repository = new InventoryRepository();
        InventoryService inventory = new InventoryService(repository);

        // ===== Step 0: prove the database connection works =====
        System.out.println("Inventory items in database: " + inventory.totalItemCount());

        // ===== Inventory slice: read and use the data =====
        System.out.println("\nFirst 10 items:");
        List<Item> items = inventory.allItems();
        for (int i = 0; i < Math.min(10, items.size()); i++) {
            System.out.println("  " + items.get(i));
        }

        System.out.println("\nItems at zero stock: " + inventory.lowStock(0).size());
        System.out.println("Items expiring within 7 days: " + inventory.expiringWithin(7).size());

        // ===== Meal planner slice: recipes =====
        RecipeRepository recipeRepository = new RecipeRepository();
        RecipeService recipes = new RecipeService(recipeRepository);

        System.out.println("\nRecipes in database: " + recipes.totalRecipeCount());
        System.out.println("Recipe list:");
        for (Recipe r : recipes.allRecipes()) {
            System.out.println("  " + r.getName() + " (" + r.getCalories() + " kcal, " + r.getCookTime() + " min)");
        }

        // ===== Recipe ingredients: prove the JOIN works =====
        int sampleRecipeId = 10;
        System.out.println("\nIngredients for recipe " + sampleRecipeId + ":");
        for (RecipeIngredient ing : recipes.findAllIngredients(sampleRecipeId)) {
            System.out.println("  " + ing.getQuantityNeeded() + " " + ing.getUnit()
                    + " " + ing.getNameEnglish()
                    + " (item #" + ing.getItemId() + ")");
        }

        // ===== Recipe instructions: prove the steps query works =====
        System.out.println("\nSteps for recipe " + sampleRecipeId + ":");
        for (RecipeInstruction step : recipes.findAllInstructions(sampleRecipeId)) {
            System.out.println("  " + step.getStepNumber() + ". " + step.getInstruction()
                    + (step.getNotes() != null ? "  [note: " + step.getNotes() + "]" : ""));
        }
    }
}
