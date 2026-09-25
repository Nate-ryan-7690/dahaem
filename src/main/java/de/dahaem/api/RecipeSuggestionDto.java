package de.dahaem.api;

import de.dahaem.suggestions.RecipeSuggestion;

import java.util.List;

/** A ranked recipe suggestion: which recipe, how strongly it matched the signal, and why. */
public record RecipeSuggestionDto(int recipeId, String recipeName, int score, List<Integer> matchingItemIds) {

    static RecipeSuggestionDto from(RecipeSuggestion suggestion) {
        return new RecipeSuggestionDto(suggestion.getRecipeId(), suggestion.getRecipeName(),
                suggestion.getScore(), suggestion.getMatchingItemIds());
    }
}
