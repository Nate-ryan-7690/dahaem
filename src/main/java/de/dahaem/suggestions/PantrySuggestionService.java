package de.dahaem.suggestions;

import de.dahaem.diet.DietFilterService;
import de.dahaem.diet.DietRules;
import de.dahaem.diet.DietVerdict;
import de.dahaem.mealplanner.Recipe;
import de.dahaem.mealplanner.RecipeQuery;
import de.dahaem.mealplanner.RecipeRepository;
import de.dahaem.mealplanner.RecipeSearchService;
import de.dahaem.shared.PageRequest;
import de.dahaem.tags.TagService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recipe suggestions that serve the purpose of the app: healthy meals (all recipe data is cooked from fresh
 * ingredients) at the lowest shopping cost. It ranks the recipes by what the household already has (items about to
 * expire first), then by how little is left to buy and how much of that is on sale, see {@link PantryRanking}.
 * It only ranks and presents: the offers are listed per store, no store and no recipe is picked for the user.
 * No SQL here; the counts come from {@link SuggestionRepository}, the filters and the sort by name or time from the
 * recipe search.
 */
public class PantrySuggestionService {

    /** Ingredients that expire within this many days count as "expiring soon" unless the caller says otherwise. */
    public static final int DEFAULT_EXPIRY_DAYS = 7;
    /** A recipe fits when at least this many of its ingredients are in stock or on sale. */
    public static final int DEFAULT_MIN_MATCHES = 1;
    /**
     * The most recipes this list ever holds (the best ones in the requested order). More are found with the recipe
     * search ({@code GET /api/recipes/search}); {@link Result#totalMatching()} says how many fit before the cap.
     */
    public static final int MAX_RESULTS = 200;

    private final SuggestionRepository suggestions;
    private final RecipeRepository recipes;
    private final TagService tags;
    private final DietFilterService diet;
    private final Clock clock;

    public PantrySuggestionService(SuggestionRepository suggestions, RecipeRepository recipes, TagService tags,
                                   DietFilterService diet, Clock clock) {
        this.suggestions = suggestions;
        this.recipes = recipes;
        this.tags = tags;
        this.diet = diet;
        this.clock = clock;
    }

    /**
     * A suggested recipe with its tag names, its coverage counts, its ingredient lines (offers on missing and expired
     * ones) and, when diet rules were given, its verdict (else null).
     */
    public record Entry(Recipe recipe, List<String> tags, PantryCoverage coverage, List<PantryLine> lines,
                        DietVerdict diet) {
    }

    /**
     * One page, the number of recipes in the list in all pages ({@code total}, at most {@link #MAX_RESULTS}), the
     * number that fit before the cap ({@code totalMatching}), and the request that produced it.
     */
    public record Result(List<Entry> items, long total, long totalMatching, PageRequest request) {

        public int totalPages() {
            return request.totalPages(total);
        }

        /** True when more recipes fit than the list holds; the rest is found with the recipe search. */
        public boolean capped() {
            return totalMatching > total;
        }
    }

    /**
     * @param q          words that must all be in the recipe name (optional)
     * @param maxTime    total minutes at most; recipes without a time are left out (optional)
     * @param store      only recipes with a missing ingredient on sale at this store (optional)
     * @param minMatches ingredients in stock or on sale needed for a recipe to show (default 1)
     * @param expiryDays look-ahead for "expiring soon" (default 7)
     * @param sort       see {@link PantrySort} (default best)
     * @param rules      the household's diet rules (hard rules hide a recipe, soft rules sort it after the others,
     *                   exactly as in the recipe search); null or no rules = no filtering
     * Bad values are an IllegalArgumentException (the API answers 400).
     */
    public Result suggest(int householdId, String q, Integer maxTime, String store, Integer minMatches,
                          Integer expiryDays, String sort, DietRules rules, PageRequest page) {
        int min = minMatches == null ? DEFAULT_MIN_MATCHES : minMatches;
        int days = expiryDays == null ? DEFAULT_EXPIRY_DAYS : expiryDays;
        if (min < 1) {
            throw new IllegalArgumentException("'minMatches' must be 1 or more");
        }
        if (days < 1) {
            throw new IllegalArgumentException("'days' must be 1 or more");
        }
        PantrySort order = PantrySort.parse(sort);
        String storeName = store == null || store.isBlank() ? null : store.trim();
        RecipeQuery query = RecipeQuery.of(q, null, null, null, maxTime, null, order == PantrySort.TIME ? "time" : "name");

        LocalDate today = LocalDate.now(clock);
        LocalDate expiryLimit = today.plusDays(days);

        List<PantryCoverage> fitting = PantryRanking.filter(
                suggestions.coverageByRecipe(householdId, expiryLimit, today, storeName), min, storeName != null);
        List<Integer> orderedIds = orderedIds(fitting, order, query);

        // Diet rules are the household's, so they apply before anything is counted or cut: a recipe it does not eat
        // is neither shown nor counted. With rules the order is the search's: no soft hit first, then OK before
        // ANPASSBAR before UNCLEAR, the requested order within each group.
        Map<Integer, DietVerdict> verdicts = null;
        if (rules != null && rules.hasRules()) {
            verdicts = orderedIds.isEmpty() ? Map.of() : diet.judgeAll(rules, orderedIds);
            orderedIds = RecipeSearchService.visibleInDisplayOrder(orderedIds, verdicts, rules);
        }
        long totalMatching = orderedIds.size();
        if (orderedIds.size() > MAX_RESULTS) {
            orderedIds = orderedIds.subList(0, MAX_RESULTS);   // the best ones; more through the recipe search
        }

        List<Integer> pageIds = slice(orderedIds, page);
        Map<Integer, PantryCoverage> coverageById = new HashMap<>();
        for (PantryCoverage c : fitting) {
            coverageById.put(c.recipeId(), c);
        }
        return new Result(entries(pageIds, coverageById, verdicts, expiryLimit, today), orderedIds.size(),
                totalMatching, page);
    }

    /** The ids of the fitting recipes in the requested order, restricted by the recipe search when it filters. */
    private List<Integer> orderedIds(List<PantryCoverage> fitting, PantrySort order, RecipeQuery query) {
        boolean byName = order == PantrySort.NAME || order == PantrySort.TIME;
        if (query.isUnfiltered() && !byName) {
            return PantryRanking.order(fitting, order).stream().map(PantryCoverage::recipeId).toList();
        }
        List<Integer> candidates = recipes.findMatchingIds(query);   // in name or time order
        Set<Integer> fits = new HashSet<>();
        for (PantryCoverage c : fitting) {
            fits.add(c.recipeId());
        }
        if (byName) {
            List<Integer> ids = new ArrayList<>();
            for (int id : candidates) {
                if (fits.contains(id)) {
                    ids.add(id);
                }
            }
            return ids;
        }
        Set<Integer> candidateSet = new LinkedHashSet<>(candidates);
        List<PantryCoverage> restricted = fitting.stream().filter(c -> candidateSet.contains(c.recipeId())).toList();
        return PantryRanking.order(restricted, order).stream().map(PantryCoverage::recipeId).toList();
    }

    /** The ids of the requested page; empty when the page is past the end. */
    static List<Integer> slice(List<Integer> ids, PageRequest page) {
        if (page.offset() >= ids.size()) {
            return List.of();
        }
        int from = (int) page.offset();
        return ids.subList(from, Math.min(ids.size(), from + page.pageSize()));
    }

    private List<Entry> entries(List<Integer> pageIds, Map<Integer, PantryCoverage> coverageById,
                                Map<Integer, DietVerdict> verdicts, LocalDate expiryLimit, LocalDate today) {
        if (pageIds.isEmpty()) {
            return List.of();
        }
        List<Recipe> page = recipes.findByIds(pageIds);
        Map<Integer, List<String>> tagNames = tags.tagNamesForRecipes(pageIds);
        Map<Integer, List<PantryLine>> lines = suggestions.pantryLines(householdId, pageIds, expiryLimit, today);

        // missing and expired ingredients are the ones still to buy, so they carry the offers on sale
        Set<Integer> toBuyItems = new LinkedHashSet<>();
        lines.values().forEach(list -> list.stream()
                .filter(PantrySuggestionService::toBuy)
                .forEach(l -> toBuyItems.add(l.itemId())));
        Map<Integer, List<PantryOffer>> offers = suggestions.offersOnSale(toBuyItems, today);

        List<Entry> out = new ArrayList<>();
        for (Recipe recipe : page) {
            List<PantryLine> withOffers = lines.getOrDefault(recipe.getId(), List.of()).stream()
                    .map(l -> toBuy(l) ? l.withOffers(offers.getOrDefault(l.itemId(), List.of())) : l)
                    .toList();
            out.add(new Entry(recipe, tagNames.getOrDefault(recipe.getId(), List.of()),
                    coverageById.get(recipe.getId()), withOffers,
                    verdicts == null ? null : verdicts.get(recipe.getId())));
        }
        return out;
    }

    private static boolean toBuy(PantryLine line) {
        return line.status() == PantryLine.Status.MISSING || line.status() == PantryLine.Status.EXPIRED;
    }
}
