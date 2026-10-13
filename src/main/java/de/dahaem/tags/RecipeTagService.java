package de.dahaem.tags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Derives the tags of every recipe and replaces ONLY the recipe_tag rows whose source is 'derived'.
 * Manual tags are never read as candidates for removal, and a tag a recipe already has by hand is not
 * added a second time. No SQL here (see {@link RecipeTagRepository}).
 */
public class RecipeTagService {

    static final int EXAMPLES_PER_TAG = 10;

    /** What a run would do (or did). */
    public record Plan(Optional<String> cuisineColumn, int recipes, Map<String, Integer> recipesPerTag,
                       Map<String, String> categoryPerTag, Map<String, List<String>> examplesPerTag,
                       int recipesDietUnknown, int recipesWithoutIngredients, int recipesWithUnlinkedIngredient,
                       List<RecipeTagRepository.Change> changes, boolean committed) {

        public int added() {
            return (int) changes.stream().filter(RecipeTagRepository.Change::add).count();
        }

        public int removed() {
            return changes.size() - added();
        }

        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append(committed ? "COMMITTED" : "DRY RUN (nothing written; add --commit to write)").append('\n');
            sb.append("recipes: ").append(recipes).append('\n');
            sb.append("cuisine tag: ").append(cuisineColumn.map(c -> "from recipe." + c)
                    .orElse("OFF (the recipe table has no cuisine column yet; it activates once migration 0010 is in)"))
                    .append('\n');
            sb.append("diet unknown (no vegetarisch / vegan tag): ").append(recipesDietUnknown)
                    .append(" (no ingredients: ").append(recipesWithoutIngredients)
                    .append(", an ingredient without item link: ").append(recipesWithUnlinkedIngredient).append(")\n");
            sb.append("changes: +").append(added()).append(" derived tags, -").append(removed())
                    .append(" derived tags\n\n");
            sb.append("tag (group): recipes carrying it, with examples\n");
            for (Map.Entry<String, Integer> e : recipesPerTag.entrySet()) {
                sb.append(String.format("%-18s (%s): %d%n", e.getKey(), categoryPerTag.get(e.getKey()), e.getValue()));
                for (String example : examplesPerTag.get(e.getKey())) {
                    sb.append("    ").append(example).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private final RecipeTagRepository repository;
    private final RecipeTagger tagger;

    public RecipeTagService(RecipeTagRepository repository, TagSettings settings) {
        this.repository = repository;
        this.tagger = new RecipeTagger(settings);
    }

    /** Reads everything and computes the plan. Writes nothing. */
    public Plan plan() {
        Optional<String> cuisineColumn = repository.cuisineColumn();
        List<RecipeFacts> recipes = repository.loadRecipes(cuisineColumn);
        Map<Integer, List<RecipeTagRepository.StoredTag>> stored = repository.loadStoredTags();

        Map<String, Integer> perTag = new TreeMap<>();
        Map<String, String> categoryPerTag = new HashMap<>();
        Map<String, List<String>> examples = new HashMap<>();
        List<RecipeTagRepository.Change> changes = new ArrayList<>();
        int dietUnknown = 0;
        int noIngredients = 0;
        int unlinked = 0;

        for (RecipeFacts recipe : recipes) {
            RecipeTagger.Derivation derivation = tagger.derive(recipe);
            if (!derivation.dietKnown()) {
                dietUnknown++;
                if (recipe.ingredients().isEmpty()) {
                    noIngredients++;
                } else {
                    unlinked++;
                }
            }
            Map<String, DerivedTag> wanted = new LinkedHashMap<>();
            for (DerivedTag tag : derivation.tags()) {
                wanted.put(tag.name(), tag);
                perTag.merge(tag.name(), 1, Integer::sum);
                categoryPerTag.putIfAbsent(tag.name(), tag.category());
                List<String> list = examples.computeIfAbsent(tag.name(), k -> new ArrayList<>());
                if (list.size() < EXAMPLES_PER_TAG) {
                    list.add("#" + recipe.id() + " " + recipe.name());
                }
            }
            Set<String> have = new HashSet<>();
            for (RecipeTagRepository.StoredTag s : stored.getOrDefault(recipe.id(), List.of())) {
                have.add(s.name());
                boolean derived = RecipeTagRepository.SOURCE_DERIVED.equals(s.source());
                if (derived && !wanted.containsKey(s.name())) {
                    changes.add(new RecipeTagRepository.Change(recipe.id(), s.name(), s.category(), false));
                }
            }
            for (DerivedTag tag : wanted.values()) {
                if (!have.contains(tag.name())) {   // a manual tag of the same name stays as it is
                    changes.add(new RecipeTagRepository.Change(recipe.id(), tag.name(), tag.category(), true));
                }
            }
        }
        return new Plan(cuisineColumn, recipes.size(), perTag, categoryPerTag, examples, dietUnknown,
                noIngredients, unlinked, changes, false);
    }

    /** Writes the plan's changes in one transaction. */
    public Plan commit(Plan plan) {
        repository.apply(plan.changes());
        return new Plan(plan.cuisineColumn(), plan.recipes(), plan.recipesPerTag(), plan.categoryPerTag(),
                plan.examplesPerTag(), plan.recipesDietUnknown(), plan.recipesWithoutIngredients(),
                plan.recipesWithUnlinkedIngredient(), plan.changes(), true);
    }

    /**
     * Every catalogue item that blocks vegetarisch (non-meat reasons only) or vegan, with the rule that
     * does it, grouped by rule. Read-only; for Nate to review the classification.
     */
    public Map<String, List<CatalogItem>> animalProducts() {
        Map<String, List<CatalogItem>> byReason = new TreeMap<>();
        for (CatalogItem item : new ItemAnimalRepository().findAllItems()) {
            AnimalProducts.vegetarianBlocker(item, false)
                    .ifPresent(r -> byReason.computeIfAbsent("not vegetarian: " + r, k -> new ArrayList<>()).add(item));
            AnimalProducts.veganBlocker(item)
                    .ifPresent(r -> byReason.computeIfAbsent("not vegan: " + r, k -> new ArrayList<>()).add(item));
        }
        return byReason;
    }
}
