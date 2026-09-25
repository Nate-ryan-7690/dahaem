package de.dahaem.suggestions;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for recipe suggestions. No SQL here, no UI here.
 * It only ranks and surfaces candidates from two signals (soon-to-expire inventory,
 * ingredients currently on sale); it never picks a recipe on the user's behalf.
 */
public class SuggestionService {

    private final SuggestionRepository repository;

    public SuggestionService(SuggestionRepository repository) {
        this.repository = repository;
    }

    /** Recipes ranked by how many ingredients are inventory items expiring within the next `days` days. */
    public List<RecipeSuggestion> suggestByExpiring(int days) {
        return repository.findRankedByExpiring(LocalDate.now().plusDays(days));
    }

    /** Recipes ranked by how many ingredients are currently on sale. */
    public List<RecipeSuggestion> suggestByDiscount() {
        return repository.findRankedByDiscount(LocalDate.now());
    }
}
