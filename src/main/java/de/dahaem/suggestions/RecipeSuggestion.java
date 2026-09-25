package de.dahaem.suggestions;

import java.util.List;

/**
 * Plain data holder for one ranked recipe suggestion: the recipe, how many of its
 * ingredients matched the signal (expiring soon / on sale), and which inventory items matched.
 * No logic here on purpose, just the fields, a constructor, and getters.
 */
public class RecipeSuggestion {

    private final int recipeId;
    private final String recipeName;
    private final int score;
    private final List<Integer> matchingItemIds;

    public RecipeSuggestion(int recipeId, String recipeName, int score, List<Integer> matchingItemIds) {
        this.recipeId = recipeId;
        this.recipeName = recipeName;
        this.score = score;
        this.matchingItemIds = matchingItemIds;
    }

    public int getRecipeId() {
        return recipeId;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public int getScore() {
        return score;
    }

    public List<Integer> getMatchingItemIds() {
        return matchingItemIds;
    }

    @Override
    public String toString() {
        return "RecipeSuggestion{" +
                "recipeId=" + recipeId +
                ", recipeName='" + recipeName + '\'' +
                ", score=" + score +
                ", matchingItemIds=" + matchingItemIds +
                '}';
    }
}
