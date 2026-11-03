package de.dahaem.diet;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Applies diet rules to recipes on the server, so the client never receives 11,000 recipes to filter itself.
 * Reusable: {@link #judgeAll} and {@link #judge} take rules and return verdicts by recipe id; the recipe list
 * (and later the full search / paging) only decides what to do with them. No SQL here.
 *
 * <p>Loading the facts of every recipe (catalogue, ingredients, tags) costs a few hundred ms, so
 * {@link #judgeAll} judges against a snapshot kept in memory for {@link #DEFAULT_CACHE_SECONDS} seconds (and dropped
 * by {@link #invalidate()}, or when the database is replaced). A listed verdict can therefore be up to that old;
 * {@link #judge} for one recipe always reads fresh, so the detail view is authoritative.
 *
 * <p>Presets filter INGREDIENTS only. DAHÄM cannot certify halal or kosher (slaughter, certification): meat
 * certification stays the shopper's responsibility.
 */
public class DietFilterService {

    public static final long DEFAULT_CACHE_SECONDS = 60;

    private final DietFilterRepository repository;
    private final DietPresets presets;
    private final long cacheNanos;
    private final LongSupplier nanoTime;
    private Snapshot snapshot;

    public DietFilterService(DietFilterRepository repository, DietPresets presets) {
        this(repository, presets, DEFAULT_CACHE_SECONDS, System::nanoTime);
    }

    /** @param cacheSeconds how long the facts of all recipes are reused; 0 reads them on every call */
    public DietFilterService(DietFilterRepository repository, DietPresets presets, long cacheSeconds,
                             LongSupplier nanoTime) {
        this.repository = repository;
        this.presets = presets;
        this.cacheNanos = cacheSeconds * 1_000_000_000L;
        this.nanoTime = nanoTime;
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
        Snapshot current = snapshot();
        Map<Integer, RecipeDietFacts> facts = current.facts();
        RecipeDietFilter filter = current.filter();
        Map<Integer, DietVerdict> out = new LinkedHashMap<>();
        for (int id : recipeIds) {
            out.put(id, filter.judge(rules, facts.getOrDefault(id,
                    new RecipeDietFacts(id, java.util.Set.of(), List.of(), List.of()))));
        }
        return out;
    }

    /** Drops the cached facts, e.g. after a tag was written through the API. */
    public synchronized void invalidate() {
        snapshot = null;
    }

    private synchronized Snapshot snapshot() {
        long now = nanoTime.getAsLong();
        long generation = repository.dataGeneration();
        if (snapshot == null || snapshot.generation() != generation || now - snapshot.loadedAt() >= cacheNanos) {
            snapshot = new Snapshot(repository.loadAll(), new RecipeDietFilter(groups(repository.tagGroups())),
                    generation, now);
        }
        return snapshot;
    }

    private record Snapshot(Map<Integer, RecipeDietFacts> facts, RecipeDietFilter filter, long generation,
                            long loadedAt) {
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
