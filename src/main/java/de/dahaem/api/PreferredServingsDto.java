package de.dahaem.api;

/** A recipe's permanent preferred servings ("this meal is always for X people"); null = no preference. */
public record PreferredServingsDto(int recipeId, Integer preferredServings) {
}
