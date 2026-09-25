package de.dahaem.api;

import de.dahaem.suggestions.SuggestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Surfaces ranked recipe suggestions; it never picks a recipe on the user's behalf,
 * it only ranks and presents (see SuggestionService).
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /** Recipes ranked by how many ingredients are currently on sale. */
    @GetMapping("/by-savings")
    public List<RecipeSuggestionDto> bySavings() {
        return suggestionService.suggestByDiscount().stream().map(RecipeSuggestionDto::from).toList();
    }

    /** Recipes ranked by how many ingredients are inventory items expiring within {@code days} days (default 7). */
    @GetMapping("/by-expiring")
    public List<RecipeSuggestionDto> byExpiring(@RequestParam(name = "days", defaultValue = "7") int days) {
        if (days < 1) {
            throw new BadRequestException("'days' must be 1 or greater");
        }
        return suggestionService.suggestByExpiring(days).stream().map(RecipeSuggestionDto::from).toList();
    }
}
