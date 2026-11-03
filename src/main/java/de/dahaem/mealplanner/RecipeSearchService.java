package de.dahaem.mealplanner;

import de.dahaem.diet.DietFilterService;
import de.dahaem.diet.DietRules;
import de.dahaem.diet.DietStatus;
import de.dahaem.diet.DietVerdict;
import de.dahaem.diet.RecipeDietFilter;
import de.dahaem.shared.PageRequest;
import de.dahaem.tags.TagService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Search, filters, diet rules and paging for the recipe list. No SQL here: the repository narrows and sorts with
 * SQL; this class decides how the diet rules fit in.
 *
 * <ul>
 *   <li>Without diet rules the database does everything: COUNT for the total, LIMIT / OFFSET for the page, and the
 *       tags are read for the rows of that page only.</li>
 *   <li>With diet rules a verdict is needed for every candidate before a page can be cut (the rules decide what is
 *       shown and in what order). The database still applies search and filters first and returns the candidate ids
 *       (ints only); the verdicts come from {@link DietFilterService}'s in-memory snapshot, so a page request does
 *       not reload the facts of 11,000 recipes. Only the recipes of the requested page are then loaded.</li>
 * </ul>
 */
public class RecipeSearchService {

    private final RecipeRepository recipes;
    private final TagService tags;
    private final DietFilterService diet;

    public RecipeSearchService(RecipeRepository recipes, TagService tags, DietFilterService diet) {
        this.recipes = recipes;
        this.tags = tags;
        this.diet = diet;
    }

    /** A recipe of a result page with its tag names and, when diet rules were given, its verdict (else null). */
    public record Entry(Recipe recipe, List<String> tags, DietVerdict diet) {
    }

    /** One page, the number of recipes that match in all pages, and the request that produced it. */
    public record Result(List<Entry> items, long total, PageRequest request) {

        public int totalPages() {
            return request.totalPages(total);
        }
    }

    public Result search(RecipeQuery query, DietRules rules, PageRequest page) {
        if (!rules.hasRules()) {
            long total = recipes.countMatching(query);
            List<Recipe> found = total <= page.offset() ? List.of()
                    : recipes.findPage(query, page.pageSize(), page.offset());
            return new Result(entries(found, null), total, page);
        }
        List<Integer> candidates = recipes.findMatchingIds(query);
        Map<Integer, DietVerdict> verdicts = candidates.isEmpty() ? Map.of() : diet.judgeAll(rules, candidates);
        List<Integer> shown = visibleInDisplayOrder(candidates, verdicts, rules);
        List<Integer> pageIds = slice(shown, page);
        return new Result(entries(recipes.findByIds(pageIds), verdicts), shown.size(), page);
    }

    /**
     * The candidates that are shown under the rules, in display order: no soft hit before soft hits, then OK,
     * ANPASSBAR, UNCLEAR; the candidates' own (sort) order is kept within a group. HIDDEN recipes are dropped, and
     * UNCLEAR ones unless {@code includeUnclear}.
     */
    static List<Integer> visibleInDisplayOrder(List<Integer> candidates, Map<Integer, DietVerdict> verdicts,
                                               DietRules rules) {
        List<Integer> shown = new ArrayList<>();
        for (int id : candidates) {
            DietStatus status = verdicts.get(id).status();
            if (status == DietStatus.OK || status == DietStatus.ANPASSBAR
                    || (status == DietStatus.UNCLEAR && rules.includeUnclear())) {
                shown.add(id);
            }
        }
        shown.sort(java.util.Comparator.comparing(verdicts::get, RecipeDietFilter.displayOrder()));   // stable
        return shown;
    }

    /** The ids of the requested page; empty when the page is past the end. */
    static List<Integer> slice(List<Integer> ids, PageRequest page) {
        if (page.offset() >= ids.size()) {
            return List.of();
        }
        int from = (int) page.offset();
        return ids.subList(from, Math.min(ids.size(), from + page.pageSize()));
    }

    private List<Entry> entries(List<Recipe> page, Map<Integer, DietVerdict> verdicts) {
        Map<Integer, List<String>> tagNames = tags.tagNamesForRecipes(page.stream().map(Recipe::getId).toList());
        return page.stream()
                .map(r -> new Entry(r, tagNames.getOrDefault(r.getId(), List.of()),
                        verdicts == null ? null : verdicts.get(r.getId())))
                .toList();
    }
}
