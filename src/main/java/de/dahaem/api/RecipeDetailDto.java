package de.dahaem.api;

import de.dahaem.mealplanner.Recipe;
import de.dahaem.mealplanner.RecipeIngredient;
import de.dahaem.mealplanner.RecipeInstruction;
import de.dahaem.mealplanner.StepAmounts;
import de.dahaem.shared.Portions;
import de.dahaem.tags.TagRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * One recipe with its nutrition, ingredients and ordered instructions, as returned by /api/recipes/{id}.
 * {@code servingsMax} is the upper bound of a "3 – 4 Personen" range ({@code servings} is then the
 * lower bound), null when the recipe is not a range. {@code people} is the head count asked for with
 * {@code ?people=N} (null when not asked); each ingredient's {@code scaledQuantity} is its
 * {@code quantity} scaled to that count, from {@code servings} by default or from {@code servingsMax}
 * when {@code basis} is {@code "max"}, and each instruction's {@code scaledInstruction} is its step text
 * with the amounts for that count. {@code basis} is the one actually used: {@code "max"} only when
 * it was asked for and the recipe is a range. {@code scalable} is false when that servings count is
 * not positive, in which case nothing is scaled. {@code tags} are the recipe's tags, sorted by group then name:
 * {@code source} is {@code "manual"} (added by a person) or {@code "derived"} (worked out from DAHÄM's data).
 */
public record RecipeDetailDto(int id, String name, int servings, Integer servingsMax, String basis, Integer people,
                              boolean scalable, int prepTime, int cookTime, Nutrition nutrition,
                              List<Ingredient> ingredients, List<Instruction> instructions, List<Tag> tags) {

    /** {@code basis} value: scaled from {@code servings} (the default, a range's lower bound). */
    public static final String BASIS_SERVINGS = "servings";
    /** {@code basis} value: scaled from {@code servingsMax} (a range's upper bound). */
    public static final String BASIS_MAX = "max";

    /** A tag on the recipe: its name, its group (e.g. Tier, Ernährung, Zeit, Nährwerte, Küche) and its source. */
    public record Tag(String name, String category, String source) {

        static Tag from(TagRepository.RecipeTag tag) {
            return new Tag(tag.name(), tag.category(), tag.source());
        }
    }

    /** Nutrition values as stored (per serving, from the HelloFresh import); any of them may be null when unknown. */
    public record Nutrition(Integer calories, Integer protein, Integer carbs, Integer fat, Integer fiber,
                            Integer sugar) {
    }

    /**
     * {@code quantity} is the stored amount for the recipe as written, as a decimal (0.5 Stück);
     * {@code scaledQuantity} is that amount for {@code people}, rounded to 2 decimals (equal to
     * {@code quantity} when unscaled). A "to taste" ingredient ({@code toTaste} true, e.g. salt) has
     * no amount: {@code quantity} and {@code scaledQuantity} are both null and {@code unit} is empty.
     */
    public record Ingredient(int itemId, String nameEnglish, String nameDeutsch, Double quantity,
                             Double scaledQuantity, String unit, boolean toTaste) {

        static Ingredient from(RecipeIngredient ingredient, Integer people, int servings) {
            BigDecimal quantity = ingredient.isToTaste() ? null : ingredient.getQuantityNeeded();
            BigDecimal scaled = quantity == null || people == null
                    ? quantity
                    : Portions.scaleRounded(quantity, people, servings);
            return new Ingredient(ingredient.getItemId(), ingredient.getNameEnglish(), ingredient.getNameDeutsch(),
                    toDouble(quantity), toDouble(scaled), ingredient.getUnit(), ingredient.isToTaste());
        }

        // JSON numbers without the column's trailing zeros: 0.5, 400.0 (not 0.500, 400.000).
        private static Double toDouble(BigDecimal value) {
            return value == null ? null : value.doubleValue();
        }
    }

    /**
     * {@code instruction} is the step as stored (HelloFresh's {@code 1 EL [1,5 EL | 2 EL] Butter*});
     * {@code scaledInstruction} is the same step with its amounts for {@code people} (see
     * {@link StepAmounts}) — without {@code people} it is the stored text with each bracket resolved to
     * the recipe's own head count. Text that can't be read safely is left exactly as written.
     */
    public record Instruction(int stepNumber, String instruction, String scaledInstruction, String notes,
                              String source) {

        static Instruction from(RecipeInstruction instruction, Recipe recipe, Integer people, boolean useMax) {
            String scaled = StepAmounts.render(instruction.getInstruction(), recipe.getServings(),
                    recipe.getServingsMax(), people, useMax);
            return new Instruction(instruction.getStepNumber(), instruction.getInstruction(), scaled,
                    instruction.getNotes(), instruction.getSource());
        }
    }

    /**
     * The recipe with ingredient quantities scaled to {@code people} (null for unscaled); people must be
     * at least 1. With {@code useMax} a range recipe scales from {@code servingsMax} instead of
     * {@code servings}; a recipe that is not a range ignores it.
     */
    static RecipeDetailDto from(Recipe recipe, List<RecipeIngredient> ingredients,
                                List<RecipeInstruction> instructions, List<TagRepository.RecipeTag> tags,
                                Integer people, boolean useMax) {
        Nutrition nutrition = new Nutrition(recipe.getCalories(), recipe.getProtein(), recipe.getCarbs(),
                recipe.getFat(), recipe.getFiber(), recipe.getSugar());
        Integer servingsMax = recipe.getServingsMax();
        int scaleFrom = Portions.scalingServings(recipe.getServings(), servingsMax, useMax);
        String basis = useMax && servingsMax != null ? BASIS_MAX : BASIS_SERVINGS;
        return new RecipeDetailDto(recipe.getId(), recipe.getName(), recipe.getServings(), servingsMax, basis, people,
                Portions.isScalable(scaleFrom), recipe.getPrepTime(), recipe.getCookTime(), nutrition,
                ingredients.stream().map(ingredient -> Ingredient.from(ingredient, people, scaleFrom)).toList(),
                instructions.stream()
                        .map(instruction -> Instruction.from(instruction, recipe, people, useMax))
                        .toList(),
                tags.stream().map(Tag::from).toList());
    }
}
