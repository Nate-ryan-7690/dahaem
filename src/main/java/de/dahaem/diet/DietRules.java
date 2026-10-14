package de.dahaem.diet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a household does not eat, as the PHP passes it (the questionnaire itself and its storage are not part of
 * the API). HARD rules ("Was esse ich nicht?") hide recipes; SOFT rules ("Was esse ich lieber nicht?") only mark
 * them. {@code requireTags} come from presets (vegetarisch, vegan). {@code includeUnclear}: also return the
 * recipes whose status is UNCLEAR (flagged).
 */
public record DietRules(Set<String> excludeTags, Set<String> requireTags, Set<Integer> excludeItems,
                        Set<String> avoidTags, Set<Integer> avoidItems, boolean includeUnclear) {

    public static DietRules none() {
        return new DietRules(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    /**
     * Resolves the presets by name and adds the explicit tags / items. An unknown preset is an
     * IllegalArgumentException (the API answers 400).
     */
    public static DietRules of(DietPresets presets, List<String> diets, List<String> excludeTags,
                               List<Integer> excludeItems, List<String> avoidTags, List<Integer> avoidItems,
                               boolean includeUnclear) {
        Set<String> exclude = new LinkedHashSet<>(clean(excludeTags));
        Set<String> require = new LinkedHashSet<>();
        for (String diet : clean(diets)) {
            DietPreset preset = presets.find(diet).orElseThrow(() -> new IllegalArgumentException(
                    "Unknown diet preset '" + diet + "'; known presets: " + String.join(", ", presets.names())));
            exclude.addAll(preset.excludeTags());
            require.addAll(preset.requireTags());
        }
        return new DietRules(exclude, require, orEmpty(excludeItems), new LinkedHashSet<>(clean(avoidTags)),
                orEmpty(avoidItems), includeUnclear);
    }

    /** True if any hard rule (exclusion or required tag) is set. */
    public boolean hasHardRules() {
        return !excludeTags.isEmpty() || !requireTags.isEmpty() || !excludeItems.isEmpty();
    }

    /** True if any rule at all is set (includeUnclear alone is no rule). */
    public boolean hasRules() {
        return hasHardRules() || !avoidTags.isEmpty() || !avoidItems.isEmpty();
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        for (String v : values) {
            for (String part : v.split(",")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    private static Set<Integer> orEmpty(List<Integer> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }
}
