package de.dahaem.mealplanner;

import java.time.LocalDate;

/**
 * Plain data holder for one planned meal: a row of meal_plan_recipe joined to its recipe and its plan.
 * {@code date} is never null: a row made by the old endpoint (no date stored) lands on the Monday of
 * its week. {@code ownServings} is what was set on the meal itself (null = follow the default);
 * {@code recipePreferredServings} is the recipe's permanent preference (null = none). The number the
 * meal is actually cooked for is worked out by {@link MealServings}.
 */
public class PlannedMeal {

    private final int entryId;
    private final int planId;
    private final int recipeId;
    private final String recipeName;
    private final String recipeThumbnailUrl;
    private final LocalDate date;
    private final String label;
    private final Integer ownServings;
    private final Integer recipePreferredServings;
    private final int position;
    private final String note;

    public PlannedMeal(int entryId, int planId, int recipeId, String recipeName, String recipeThumbnailUrl,
                       LocalDate date, String label, Integer ownServings, Integer recipePreferredServings,
                       int position, String note) {
        this.entryId = entryId;
        this.planId = planId;
        this.recipeId = recipeId;
        this.recipeName = recipeName;
        this.recipeThumbnailUrl = recipeThumbnailUrl;
        this.date = date;
        this.label = label;
        this.ownServings = ownServings;
        this.recipePreferredServings = recipePreferredServings;
        this.position = position;
        this.note = note;
    }

    public int getEntryId() { return entryId; }

    public int getPlanId() { return planId; }

    public int getRecipeId() { return recipeId; }

    public String getRecipeName() { return recipeName; }

    public String getRecipeThumbnailUrl() { return recipeThumbnailUrl; }

    public LocalDate getDate() { return date; }

    public String getLabel() { return label; }

    public Integer getOwnServings() { return ownServings; }

    public Integer getRecipePreferredServings() { return recipePreferredServings; }

    public int getPosition() { return position; }

    public String getNote() { return note; }

    @Override
    public String toString() {
        return "PlannedMeal{entryId=" + entryId + ", planId=" + planId + ", recipeId=" + recipeId
                + ", date=" + date + ", label='" + label + "', ownServings=" + ownServings
                + ", position=" + position + "}";
    }
}
