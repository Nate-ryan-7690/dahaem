package de.dahaem.mealplanner;

/**
 * Plain data holder for one row of the meal_plan_recipe table, joined to the recipe's name.
 * No logic here on purpose just the fields, a constructor, and getters.
 */

public class MealPlanRecipe {

    private final int recipeId;
    private final int planId;
    private final String mealTime;
    private final String recipeName;

    public MealPlanRecipe(int recipeId, int planId, String mealTime, String recipeName) {
        this.recipeId = recipeId;
        this.planId = planId;
        this.mealTime = mealTime;
        this.recipeName = recipeName;
    }

    public int getRecipeId() { return recipeId; }

    public int getPlanId() { return planId; }

    public String getMealTime() { return mealTime; }

    public String getRecipeName() { return recipeName; }

    @Override
    public String toString() {
        return "MealPlanRecipe{" +
                "recipeId=" + recipeId +
                ", planId=" + planId +
                ", mealTime='" + mealTime + '\'' +
                ", recipeName='" + recipeName + '\'' +
                "}";
    }
}
