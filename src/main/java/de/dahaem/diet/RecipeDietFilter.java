package de.dahaem.diet;

import de.dahaem.tags.Animals;
import de.dahaem.tags.AnimalProducts;
import de.dahaem.tags.CatalogItem;
import de.dahaem.tags.DerivedTag;
import de.dahaem.tags.RecipeFacts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Judges one recipe against {@link DietRules}. Pure: no SQL, no I/O; reusable for the full recipe search.
 *
 * <p>The principle is "surface, don't decide, never claim what is not known": a recipe is only OK when nothing in it
 * is excluded AND nothing about it is unknown. The facts are checked both through the stored tags and directly from
 * the ingredients, so a stale tag can only make the answer safer, never less safe:
 * <ul>
 *   <li>an excluded tag the recipe has, an excluded animal of a certain item, an excluded item → HIDDEN;</li>
 *   <li>an excluded animal that only a POSSIBLE animal of a generic item could bring: ANPASSBAR with a hint per
 *       item (the animals it may still be); when every possible animal is excluded the item cannot be saved →
 *       HIDDEN;</li>
 *   <li>a required tag (vegetarisch, vegan): satisfied only with the tag, no unmatched line, and no ingredient that
 *       blocks it; a missing tag with unmatched lines and no known blocker → UNCLEAR, otherwise HIDDEN;</li>
 *   <li>a hard rule that depends on the ingredients (Tier, Ernährung, Inhalt tags, items) and a recipe with
 *       unmatched lines or without ingredients → UNCLEAR;</li>
 *   <li>soft rules never change the status; they list what matched.</li>
 * </ul>
 */
public final class RecipeDietFilter {

    /** Tag groups whose tags are derived from the ingredients; a hard rule on them cannot be judged for an unclear recipe. */
    private static final Set<String> INGREDIENT_GROUPS = Set.of(DerivedTag.TIER, DerivedTag.ERNAEHRUNG, DerivedTag.INHALT);
    private static final Set<String> INGREDIENT_TAGS = Set.of(DerivedTag.ALKOHOL, DerivedTag.FLEISCH_MIT_MILCH,
            DerivedTag.VEGETARISCH, DerivedTag.VEGAN, DerivedTag.UNKLAR);

    private final Predicate<String> ingredientTag;

    /**
     * @param tagGroupOf looks up the group (tag_category) of a stored tag, null if the tag does not exist
     */
    public RecipeDietFilter(java.util.function.Function<String, String> tagGroupOf) {
        this.ingredientTag = name -> Animals.isAnimal(name) || INGREDIENT_TAGS.contains(name)
                || INGREDIENT_GROUPS.contains(tagGroupOf.apply(name));
    }

    public DietVerdict judge(DietRules rules, RecipeDietFacts recipe) {
        boolean unclear = !recipe.unmatched().isEmpty() || recipe.ingredients().isEmpty()
                || recipe.ingredients().stream().anyMatch(i -> i.item() == null);

        Set<String> tags = effectiveTags(recipe, unclear);
        Set<Integer> itemIds = new LinkedHashSet<>();
        recipe.ingredients().forEach(i -> {
            if (i.item() != null) {
                itemIds.add(i.item().id());
            }
        });

        // ---- hard rules ----
        boolean hidden = false;
        boolean dependsOnIngredients = !rules.excludeItems().isEmpty() || !rules.requireTags().isEmpty();
        for (String excluded : rules.excludeTags()) {
            if (tags.contains(excluded)) {
                hidden = true;
            }
            if (ingredientTag.test(excluded)) {
                dependsOnIngredients = true;
            }
        }
        for (int item : rules.excludeItems()) {
            if (itemIds.contains(item)) {
                hidden = true;
            }
        }
        List<DietVerdict.Hint> hints = possibleHints(recipe, excludedAnimals(rules.excludeTags()));
        if (hints.stream().anyMatch(h -> h.allowedAnimals().isEmpty())) {
            hidden = true;   // every animal the item may be from is excluded
        }
        boolean requiredMissingUnclear = false;
        for (String required : rules.requireTags()) {
            if (satisfies(required, recipe, tags, unclear)) {
                continue;
            }
            if (isDietTag(required) && unclear && !blockedByKnown(required, recipe)) {
                requiredMissingUnclear = true;
            } else {
                hidden = true;
            }
        }

        DietStatus status;
        if (hidden) {
            status = DietStatus.HIDDEN;
        } else if (requiredMissingUnclear || (dependsOnIngredients && unclear)) {
            status = DietStatus.UNCLEAR;
        } else if (!hints.isEmpty()) {
            status = DietStatus.ANPASSBAR;
        } else {
            status = DietStatus.OK;
        }
        if (status != DietStatus.ANPASSBAR && status != DietStatus.UNCLEAR) {
            hints = List.of();   // nothing to adapt: the recipe is fine as it is, or not shown
        }
        return new DietVerdict(status, hints, soft(rules, recipe, tags, itemIds));
    }

    /** Orders recipes for the response: no soft hit before soft hits, then OK, ANPASSBAR, UNCLEAR. */
    public static Comparator<DietVerdict> displayOrder() {
        return Comparator.comparing((DietVerdict v) -> v.hasSoftHits() ? 1 : 0)
                .thenComparing(v -> v.status().ordinal());
    }

    // ---------- helpers ----------

    /** Stored tags plus what the ingredients say right now (a stale tag run can only make this stricter). */
    private static Set<String> effectiveTags(RecipeDietFacts recipe, boolean unclear) {
        Set<String> tags = new LinkedHashSet<>(recipe.tags());
        boolean meat = false;
        boolean milk = false;
        for (RecipeFacts.Ingredient i : recipe.ingredients()) {
            tags.addAll(i.animals());
            if (i.item() != null && i.item().alcohol()) {
                tags.add(DerivedTag.ALKOHOL);
            }
            if (i.animals().stream().anyMatch(Animals::isLandAnimal)
                    || i.possibleAnimals().stream().anyMatch(Animals::isLandAnimal)) {
                meat = true;
            } else if (i.item() != null && !i.hasAnyAnimal() && AnimalProducts.isMilkProduct(i.item())) {
                milk = true;
            }
        }
        if (meat && milk) {
            tags.add(DerivedTag.FLEISCH_MIT_MILCH);
        }
        if (unclear) {
            tags.add(DerivedTag.UNKLAR);
        }
        return tags;
    }

    private static boolean isDietTag(String tag) {
        return tag.equals(DerivedTag.VEGETARISCH) || tag.equals(DerivedTag.VEGAN);
    }

    private static boolean satisfies(String required, RecipeDietFacts recipe, Set<String> tags, boolean unclear) {
        if (!tags.contains(required)) {
            return false;
        }
        if (isDietTag(required)) {
            return !unclear && !blockedByKnown(required, recipe);
        }
        return true;
    }

    /** True if an ingredient we do know already rules the diet out (so unmatched lines cannot change the answer). */
    private static boolean blockedByKnown(String dietTag, RecipeDietFacts recipe) {
        for (RecipeFacts.Ingredient i : recipe.ingredients()) {
            CatalogItem item = i.item();
            if (item == null) {
                continue;
            }
            if (AnimalProducts.vegetarianBlocker(item, i.hasAnyAnimal()).isPresent()) {
                return true;
            }
            if (dietTag.equals(DerivedTag.VEGAN) && AnimalProducts.veganBlocker(item).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> excludedAnimals(Set<String> tags) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : tags) {
            if (Animals.isAnimal(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Items with a possible animal that is in {@code animals}, each with the animals it may still be. */
    private static List<DietVerdict.Hint> possibleHints(RecipeDietFacts recipe, Set<String> animals) {
        List<DietVerdict.Hint> hints = new ArrayList<>();
        if (animals.isEmpty()) {
            return hints;
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (RecipeFacts.Ingredient i : recipe.ingredients()) {
            if (i.item() == null || i.possibleAnimals().isEmpty() || !seen.add(i.item().id())) {
                continue;
            }
            if (i.possibleAnimals().stream().noneMatch(animals::contains)) {
                continue;
            }
            Set<String> allowed = new LinkedHashSet<>(i.possibleAnimals());
            allowed.removeAll(animals);
            hints.add(new DietVerdict.Hint(i.item().id(), i.item().name(), Animals.sorted(allowed),
                    Animals.sorted(new LinkedHashSet<>(i.possibleAnimals()))));
        }
        return hints;
    }

    private static DietVerdict.SoftHits soft(DietRules rules, RecipeDietFacts recipe, Set<String> tags,
                                             Set<Integer> itemIds) {
        if (rules.avoidTags().isEmpty() && rules.avoidItems().isEmpty()) {
            return null;
        }
        List<String> hitTags = new ArrayList<>();
        for (String t : rules.avoidTags()) {
            if (tags.contains(t)) {
                hitTags.add(t);
            }
        }
        List<DietVerdict.Item> hitItems = new ArrayList<>();
        for (RecipeFacts.Ingredient i : recipe.ingredients()) {
            if (i.item() != null && rules.avoidItems().contains(i.item().id())
                    && hitItems.stream().noneMatch(x -> x.itemId() == i.item().id())) {
                hitItems.add(new DietVerdict.Item(i.item().id(), i.item().name()));
            }
        }
        List<DietVerdict.Hint> possible = possibleHints(recipe, excludedAnimals(rules.avoidTags()));
        DietVerdict.SoftHits hits = new DietVerdict.SoftHits(hitTags, hitItems, possible);
        return hits.isEmpty() ? null : hits;
    }
}
