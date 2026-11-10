package de.dahaem.api;

import de.dahaem.diet.DietVerdict;
import de.dahaem.suggestions.PantryCoverage;
import de.dahaem.suggestions.PantrySuggestionService;

import java.util.List;

/**
 * A suggested recipe and why it fits: the recipe card, how many of its ingredients are needed ("to taste" ones not
 * counted), how many are in stock, how many of those expire soon, how many are missing and how many of the missing
 * ones are on sale, and every ingredient with its state. Counts are of ingredients, not of amounts: an ingredient is
 * in stock when the household has any of it that has not expired; stock past its date is shown as {@code EXPIRED} and
 * counts as still to buy ({@code missing}). With diet rules the recipe card carries {@code diet} (status OK / ANPASSBAR
 * / UNCLEAR, hints, softHits), as in the recipe search. Nothing here is a choice made for the user.
 */
public record PantrySuggestionDto(RecipeCardDto recipe, int ingredientsNeeded, int inStock, int expiringSoon,
                                  int missing, int missingOnSale, List<PantryIngredientDto> ingredients) {

    static PantrySuggestionDto from(PantrySuggestionService.Entry entry) {
        PantryCoverage c = entry.coverage();
        DietVerdict verdict = entry.diet();
        return new PantrySuggestionDto(RecipeCardDto.from(entry.recipe(), entry.tags(),
                verdict == null ? null : DietDto.from(verdict, null)), c.needed(),
                c.inStock(), c.expiring(), c.missing(), c.missingOnSale(),
                entry.lines().stream().map(PantryIngredientDto::from).toList());
    }
}
