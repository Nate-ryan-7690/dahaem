package de.dahaem.api;

import de.dahaem.auth.HouseholdEquipmentService;
import de.dahaem.diet.DietFilterService;
import de.dahaem.diet.DietRules;
import de.dahaem.diet.DietStatus;
import de.dahaem.diet.DietVerdict;
import de.dahaem.shared.PageRequest;
import de.dahaem.suggestions.PantrySuggestionService;
import de.dahaem.suggestions.RecipeSuggestion;
import de.dahaem.suggestions.SuggestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Surfaces ranked recipe suggestions; it never picks a recipe on the user's behalf,
 * it only ranks and presents (see SuggestionService).
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final PantrySuggestionService pantrySuggestions;
    private final DietFilterService dietFilter;
    private final HouseholdEquipmentService equipment;

    public SuggestionController(SuggestionService suggestionService, PantrySuggestionService pantrySuggestions,
                                DietFilterService dietFilter, HouseholdEquipmentService equipment) {
        this.suggestionService = suggestionService;
        this.pantrySuggestions = pantrySuggestions;
        this.dietFilter = dietFilter;
        this.equipment = equipment;
    }

    /** The recipe tags of the equipment the caller's household does not have: those recipes are never suggested. */
    private List<RecipeSuggestion> withoutRecipesNeedingMissingEquipment(List<RecipeSuggestion> suggestions, Caller caller) {
        List<String> excluded = equipment.excludedRecipeTags(caller.householdId());
        if (excluded.isEmpty() || suggestions.isEmpty()) {
            return suggestions;
        }
        DietRules rules = dietFilter.rules(null, excluded, null, null, null, false);
        Map<Integer, DietVerdict> verdicts = dietFilter.judgeAll(rules,
                suggestions.stream().map(RecipeSuggestion::getRecipeId).toList());
        return suggestions.stream().filter(s -> {
            DietVerdict verdict = verdicts.get(s.getRecipeId());
            return verdict == null || verdict.status() != DietStatus.HIDDEN;
        }).toList();
    }

    /** Recipes ranked by how many ingredients are currently on sale. */
    @GetMapping("/by-savings")
    public List<RecipeSuggestionDto> bySavings(Caller caller) {
        return withoutRecipesNeedingMissingEquipment(suggestionService.suggestByDiscount(), caller).stream()
                .map(RecipeSuggestionDto::from).toList();
    }

    /** Recipes ranked by how many ingredients are inventory items expiring within {@code days} days (default 7). */
    @GetMapping("/by-expiring")
    public List<RecipeSuggestionDto> byExpiring(@RequestParam(name = "days", defaultValue = "7") int days,
                                                Caller caller) {
        if (days < 1) {
            throw new BadRequestException("'days' must be 1 or greater");
        }
        return withoutRecipesNeedingMissingEquipment(suggestionService.suggestByExpiring(caller.householdId(), days), caller)
                .stream().map(RecipeSuggestionDto::from).toList();
    }

    /**
     * {@code GET /api/suggestions/pantry}: recipes that fit what the household has and what is on sale, one page of
     * suggestions plus the total. The purpose is healthy meals (all recipe data is cooked from fresh ingredients) at
     * the lowest shopping cost, so the default order {@code best} uses up the pantry first (ingredients that expire
     * soon weigh most), then needs the fewest ingredients bought at full price; pantry ingredients rank a recipe
     * higher, never lower. Every suggestion says which ingredients are in stock, which expire soon and which are
     * missing, and lists the offers on sale today for each missing one, every store, never one picked. All filters
     * are optional and combine with AND:
     * <ul>
     *   <li>{@code q=<words>} (every word is in the recipe name), {@code maxTime} (total minutes at most; recipes
     *       without a time are left out), {@code store=<name>} (a missing ingredient is on sale at that store);</li>
     *   <li>{@code minMatches} (ingredients in stock or on sale needed to show a recipe, default 1) and
     *       {@code days} (look-ahead for "expiring soon", default 7);</li>
     *   <li>{@code sort=best|fewest-to-buy|expiring|name|time} (default best; ties end by recipe id, so paging is
     *       stable);</li>
     *   <li>paging: {@code page} (from 1) and {@code pageSize} (1 - 100, default 24). Out-of-range values are a
     *       400.</li>
     * </ul>
     * The diet rules of {@code GET /api/recipes/search} apply here too, on the server: hard rules
     * ({@code diet}, {@code excludeTags}, {@code excludeItems}) hide a recipe the household does not eat and it is
     * not counted, soft rules ({@code avoidTags}, {@code avoidItems}) sort it after the others, and
     * {@code includeUnclear} (default false) lets recipes with an unmatched line through. With rules each recipe
     * card carries {@code diet}, and the order is: no soft hit first, then OK before ANPASSBAR before UNCLEAR, the
     * requested {@code sort} within each group.
     * <p>The list holds at most 200 recipes, the best ones in the requested order ({@code total} is at most 200;
     * {@code totalMatching} says how many fit before the cap and {@code capped} whether it was cut); more are found
     * with {@code GET /api/recipes/search}.
     * <p>An ingredient counts as in stock when the household has any of it and it has not expired; the amount is not
     * compared with the amount the recipe needs yet. Stock past its date is shown as {@code EXPIRED} and counts as
     * still to buy. This replaces the whole-list {@code /by-savings} for pages: that call is unchanged.
     */
    @GetMapping("/pantry")
    public PantrySuggestionPageDto pantry(@RequestParam(name = "q", required = false) String q,
                                          @RequestParam(name = "maxTime", required = false) Integer maxTime,
                                          @RequestParam(name = "store", required = false) String store,
                                          @RequestParam(name = "minMatches", required = false) Integer minMatches,
                                          @RequestParam(name = "days", required = false) Integer days,
                                          @RequestParam(name = "sort", required = false) String sort,
                                          @RequestParam(name = "page", required = false) Integer page,
                                          @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                          @RequestParam(name = "diet", required = false) List<String> diet,
                                          @RequestParam(name = "excludeTags", required = false) List<String> excludeTags,
                                          @RequestParam(name = "excludeItems", required = false) List<Integer> excludeItems,
                                          @RequestParam(name = "avoidTags", required = false) List<String> avoidTags,
                                          @RequestParam(name = "avoidItems", required = false) List<Integer> avoidItems,
                                          @RequestParam(name = "includeUnclear", defaultValue = "false") boolean includeUnclear,
                                          Caller caller) {
        PageRequest request = PageRequest.of(page, pageSize);
        List<String> householdExcluded = equipment.excludedRecipeTags(caller.householdId());
        if (!householdExcluded.isEmpty()) {
            excludeTags = new java.util.ArrayList<>(excludeTags == null ? List.of() : excludeTags);
            excludeTags.addAll(householdExcluded);
        }
        DietRules rules = dietFilter.rules(diet, excludeTags, excludeItems, avoidTags, avoidItems, includeUnclear);
        PantrySuggestionService.Result result = pantrySuggestions.suggest(caller.householdId(), q, maxTime, store, minMatches, days, sort,
                rules, request);
        return new PantrySuggestionPageDto(result.items().stream().map(PantrySuggestionDto::from).toList(),
                result.total(), result.totalMatching(), result.capped(), request.page(), request.pageSize(),
                result.totalPages());
    }
}
