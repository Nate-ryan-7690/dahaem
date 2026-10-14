package de.dahaem.diet;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies diet rules to recipes on the server, so the client never receives 11,000 recipes to filter itself.
 * Reusable: {@link #judgeAll} and {@link #judge} take rules and return verdicts by recipe id; the recipe list
 * (and later the full search / paging) only decides what to do with them. No SQL here.
 *
 * <p>Presets filter INGREDIENTS only. DAHÄM cannot certify halal or kosher (slaughter, certification): meat
 * certification stays the shopper's responsibility.
 */
public class DietFilterService {

    private final DietFilterRepository repository;
    private final DietPresets presets;

    public DietFilterService(DietFilterRepository repository, DietPresets presets) {
        this.repository = repository;
        this.presets = presets;
    }

    public DietPresets presets() {
        return presets;
    }

    /** The rules for the given request parameters; an unknown preset is an IllegalArgumentException. */
    public DietRules rules(List<String> diets, List<String> excludeTags, List<Integer> excludeItems,
                           List<String> avoidTags, List<Integer> avoidItems, boolean includeUnclear) {
        return DietRules.of(presets, diets, excludeTags, excludeItems, avoidTags, avoidItems, includeUnclear);
    }

    /** A verdict for EVERY recipe that has any facts, by recipe id (recipes without ingredients are UNCLEAR). */
    public Map<Integer, DietVerdict> judgeAll(DietRules rules, Iterable<Integer> recipeIds) {
        Map<Integer, RecipeDietFacts> facts = repository.loadAll();
        RecipeDietFilter filter = new RecipeDietFilter(groups(repository.tagGroups()));
        Map<Integer, DietVerdict> out = new LinkedHashMap<>();
        for (int id : recipeIds) {
            out.put(id, filter.judge(rules, facts.getOrDefault(id,
                    new RecipeDietFacts(id, java.util.Set.of(), List.of(), List.of()))));
        }
        return out;
    }

    /** The verdict for one recipe, with its unmatched lines in {@link RecipeDietFacts#unmatched()}. */
    public Judged judge(DietRules rules, int recipeId) {
        RecipeDietFacts facts = repository.loadRecipe(recipeId);
        RecipeDietFilter filter = new RecipeDietFilter(groups(repository.tagGroups()));
        return new Judged(filter.judge(rules, facts), facts);
    }

    /** A verdict together with the facts it was made from. */
    public record Judged(DietVerdict verdict, RecipeDietFacts facts) {
    }

    private static java.util.function.Function<String, String> groups(Map<String, String> groups) {
        return new HashMap<>(groups)::get;
    }
}
