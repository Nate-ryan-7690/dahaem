package de.dahaem.api;

/** Body of POST /api/meal-plans/{id}/recipes, e.g. {@code {"recipeId": 3, "mealTime": "Abendessen"}}. */
public record AddPlannedRecipeRequest(Integer recipeId, String mealTime) {
}
