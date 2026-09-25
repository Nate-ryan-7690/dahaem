package de.dahaem.api;

import de.dahaem.mealplanner.Recipe;
import de.dahaem.mealplanner.RecipeIngredient;
import de.dahaem.mealplanner.RecipeInstruction;

import java.util.List;

/** One recipe with its nutrition, ingredients and ordered instructions, as returned by /api/recipes/{id}. */
public record RecipeDetailDto(int id, String name, int servings, int prepTime, int cookTime, Nutrition nutrition,
                              List<Ingredient> ingredients, List<Instruction> instructions) {

    /** Per-recipe nutrition values; any of them may be null when unknown. */
    public record Nutrition(Integer calories, Integer protein, Integer carbs, Integer fat, Integer fiber,
                            Integer sugar) {
    }

    public record Ingredient(int itemId, String nameEnglish, String nameDeutsch, int quantity, String unit) {

        static Ingredient from(RecipeIngredient ingredient) {
            return new Ingredient(ingredient.getItemId(), ingredient.getNameEnglish(), ingredient.getNameDeutsch(),
                    ingredient.getQuantityNeeded(), ingredient.getUnit());
        }
    }

    public record Instruction(int stepNumber, String instruction, String notes, String source) {

        static Instruction from(RecipeInstruction instruction) {
            return new Instruction(instruction.getStepNumber(), instruction.getInstruction(),
                    instruction.getNotes(), instruction.getSource());
        }
    }

    static RecipeDetailDto from(Recipe recipe, List<RecipeIngredient> ingredients,
                                List<RecipeInstruction> instructions) {
        Nutrition nutrition = new Nutrition(recipe.getCalories(), recipe.getProtein(), recipe.getCarbs(),
                recipe.getFat(), recipe.getFiber(), recipe.getSugar());
        return new RecipeDetailDto(recipe.getId(), recipe.getName(), recipe.getServings(), recipe.getPrepTime(),
                recipe.getCookTime(), nutrition,
                ingredients.stream().map(Ingredient::from).toList(),
                instructions.stream().map(Instruction::from).toList());
    }
}
