package de.dahaem.api;

import de.dahaem.mealplanner.Recipe;
import de.dahaem.mealplanner.RecipeIngredient;
import de.dahaem.mealplanner.RecipeInstruction;
import de.dahaem.shared.Portions;

import java.util.List;

/**
 * One recipe with its nutrition, ingredients and ordered instructions, as returned by /api/recipes/{id}.
 * {@code people} is the head count asked for with {@code ?people=N} (null when not asked); each
 * ingredient's {@code scaledQuantity} is its {@code quantity} scaled to that count. {@code scalable}
 * is false when the recipe has no positive servings, in which case nothing is scaled.
 */
public record RecipeDetailDto(int id, String name, int servings, Integer people, boolean scalable, int prepTime,
                              int cookTime, Nutrition nutrition, List<Ingredient> ingredients,
                              List<Instruction> instructions) {

    /** Nutrition values as stored (per serving, from the HelloFresh import); any of them may be null when unknown. */
    public record Nutrition(Integer calories, Integer protein, Integer carbs, Integer fat, Integer fiber,
                            Integer sugar) {
    }

    /**
     * {@code quantity} is the stored amount for the recipe's {@code servings}; {@code scaledQuantity} is
     * that amount for {@code people}, rounded to 2 decimals (equal to {@code quantity} when unscaled).
     */
    public record Ingredient(int itemId, String nameEnglish, String nameDeutsch, int quantity,
                             double scaledQuantity, String unit) {

        static Ingredient from(RecipeIngredient ingredient, Integer people, int servings) {
            int quantity = ingredient.getQuantityNeeded();
            double scaled = people == null ? quantity : Portions.scaleRounded(quantity, people, servings);
            return new Ingredient(ingredient.getItemId(), ingredient.getNameEnglish(), ingredient.getNameDeutsch(),
                    quantity, scaled, ingredient.getUnit());
        }
    }

    public record Instruction(int stepNumber, String instruction, String notes, String source) {

        static Instruction from(RecipeInstruction instruction) {
            return new Instruction(instruction.getStepNumber(), instruction.getInstruction(),
                    instruction.getNotes(), instruction.getSource());
        }
    }

    /** The recipe with ingredient quantities scaled to {@code people} (null for unscaled); people must be at least 1. */
    static RecipeDetailDto from(Recipe recipe, List<RecipeIngredient> ingredients,
                                List<RecipeInstruction> instructions, Integer people) {
        Nutrition nutrition = new Nutrition(recipe.getCalories(), recipe.getProtein(), recipe.getCarbs(),
                recipe.getFat(), recipe.getFiber(), recipe.getSugar());
        int servings = recipe.getServings();
        return new RecipeDetailDto(recipe.getId(), recipe.getName(), servings, people, Portions.isScalable(servings),
                recipe.getPrepTime(), recipe.getCookTime(), nutrition,
                ingredients.stream().map(ingredient -> Ingredient.from(ingredient, people, servings)).toList(),
                instructions.stream().map(Instruction::from).toList());
    }
}
