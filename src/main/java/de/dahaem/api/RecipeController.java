package de.dahaem.api;

import de.dahaem.diet.DietFilterService;
import de.dahaem.diet.DietRules;
import de.dahaem.diet.DietStatus;
import de.dahaem.diet.DietVerdict;
import de.dahaem.diet.RecipeDietFilter;
import de.dahaem.mealplanner.Recipe;
import de.dahaem.mealplanner.RecipeService;
import de.dahaem.shared.Portions;
import de.dahaem.tags.TagService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final TagService tagService;
    private final DietFilterService dietFilter;

    public RecipeController(RecipeService recipeService, TagService tagService, DietFilterService dietFilter) {
        this.recipeService = recipeService;
        this.tagService = tagService;
        this.dietFilter = dietFilter;
    }

    /**
     * All recipes by name. Optional diet rules, applied on the server (the client never filters 11,000 recipes):
     * <ul>
     *   <li>hard rules ("Was esse ich nicht?"): {@code diet=<preset>} (repeatable; halal, koscher, vegetarisch,
     *       vegan, see config/diet_presets.json; an unknown preset is a 400), {@code excludeTags=a,b},
     *       {@code excludeItems=<item ids>};</li>
     *   <li>soft rules ("Was esse ich lieber nicht?"): {@code avoidTags}, {@code avoidItems}: recipes stay, are
     *       sorted after the others and list what matched in {@code diet.softHits};</li>
     *   <li>{@code includeUnclear=true|false} (default false): recipes with a line that was not matched are
     *       UNCLEAR under a hard rule and left out unless this is true.</li>
     * </ul>
     * With rules every recipe carries {@code diet} (status OK / ANPASSBAR / UNCLEAR, hints, softHits); HIDDEN
     * recipes are not returned. The presets filter INGREDIENTS only; they are no halal or kosher certification:
     * meat certification stays the shopper's responsibility. Without any rule the answer is unchanged.
     */
    @GetMapping
    public List<RecipeSummaryDto> all(@RequestParam(name = "diet", required = false) List<String> diet,
                                      @RequestParam(name = "excludeTags", required = false) List<String> excludeTags,
                                      @RequestParam(name = "excludeItems", required = false) List<Integer> excludeItems,
                                      @RequestParam(name = "avoidTags", required = false) List<String> avoidTags,
                                      @RequestParam(name = "avoidItems", required = false) List<Integer> avoidItems,
                                      @RequestParam(name = "includeUnclear", defaultValue = "false") boolean includeUnclear) {
        DietRules rules = dietFilter.rules(diet, excludeTags, excludeItems, avoidTags, avoidItems, includeUnclear);
        Map<Integer, List<String>> tags = tagService.tagNamesByRecipe();
        List<Recipe> recipes = recipeService.allRecipes();
        if (!rules.hasRules()) {
            return recipes.stream()
                    .map(recipe -> RecipeSummaryDto.from(recipe, tags.getOrDefault(recipe.getId(), List.of())))
                    .toList();
        }
        Map<Integer, DietVerdict> verdicts = dietFilter.judgeAll(rules, recipes.stream().map(Recipe::getId).toList());
        List<Recipe> shown = new ArrayList<>();
        for (Recipe recipe : recipes) {
            DietStatus status = verdicts.get(recipe.getId()).status();
            if (status == DietStatus.OK || status == DietStatus.ANPASSBAR
                    || (status == DietStatus.UNCLEAR && rules.includeUnclear())) {
                shown.add(recipe);
            }
        }
        shown.sort(Comparator.comparing((Recipe r) -> verdicts.get(r.getId()), RecipeDietFilter.displayOrder()));   // stable: name order within a group
        return shown.stream()
                .map(recipe -> RecipeSummaryDto.from(recipe, tags.getOrDefault(recipe.getId(), List.of()),
                        DietDto.from(verdicts.get(recipe.getId()), null)))
                .toList();
    }

    /**
     * The recipe with its ingredients and instructions. An optional {@code ?people=N} adds each
     * ingredient's quantity scaled by {@code people / servings} as {@code scaledQuantity}; the stored
     * {@code quantity}, the stored instruction text and the nutrition are never changed; each instruction's
     * {@code scaledInstruction} has its step amounts for {@code people} (a bracket always means 2 / 3 / 4 people;
     * otherwise the 2-person value scaled). An optional
     * {@code ?basis=max} scales a "3 – 4 Personen" range recipe from its upper bound
     * ({@code people / servingsMax}) instead; it is ignored for a recipe that is not a range, and
     * {@code ?basis=servings} is the default. The diet parameters of {@code GET /api/recipes} are accepted here
     * too: with any of them the answer carries {@code diet} with the same status and hints and the recipe's
     * unmatched ingredient lines (the recipe is returned even when its status is HIDDEN).
     */
    @GetMapping("/{id}")
    public RecipeDetailDto byId(@PathVariable("id") int id,
                                @RequestParam(name = "people", required = false) Integer people,
                                @RequestParam(name = "basis", required = false) String basis,
                                @RequestParam(name = "diet", required = false) List<String> diet,
                                @RequestParam(name = "excludeTags", required = false) List<String> excludeTags,
                                @RequestParam(name = "excludeItems", required = false) List<Integer> excludeItems,
                                @RequestParam(name = "avoidTags", required = false) List<String> avoidTags,
                                @RequestParam(name = "avoidItems", required = false) List<Integer> avoidItems,
                                @RequestParam(name = "includeUnclear", defaultValue = "false") boolean includeUnclear) {
        if (people != null && !Portions.isValidPeople(people)) {
            throw new BadRequestException(Portions.INVALID_PEOPLE_MESSAGE);
        }
        if (basis != null && !basis.equals(RecipeDetailDto.BASIS_SERVINGS) && !basis.equals(RecipeDetailDto.BASIS_MAX)) {
            throw new BadRequestException(Portions.INVALID_BASIS_MESSAGE);
        }
        DietRules rules = dietFilter.rules(diet, excludeTags, excludeItems, avoidTags, avoidItems, includeUnclear);
        Recipe recipe = requireRecipe(id);
        DietDto dietDto = null;
        if (rules.hasRules()) {
            DietFilterService.Judged judged = dietFilter.judge(rules, id);
            dietDto = DietDto.from(judged.verdict(), judged.facts().unmatched());
        }
        return RecipeDetailDto.from(recipe, recipeService.findAllIngredients(id), recipeService.findAllInstructions(id),
                tagService.recipeTagsForRecipe(id), people, RecipeDetailDto.BASIS_MAX.equals(basis), dietDto);
    }

    @GetMapping("/{id}/tags")
    public List<TagDto> tags(@PathVariable("id") int id) {
        requireRecipe(id);
        return tagService.tagsForRecipe(id).stream().map(TagDto::from).toList();
    }

    /** Tags the recipe, creating the tag first if no tag with that name exists. Idempotent per (recipe, tag). */
    @PostMapping("/{id}/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagDto addTag(@PathVariable("id") int id, @RequestBody AddTagRequest request) {
        requireRecipe(id);
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("'name' is required");
        }
        if (request.category() == null || request.category().isBlank()) {
            throw new BadRequestException("'category' is required");
        }
        return TagDto.from(tagService.addTagToRecipe(id, request.name().trim(), request.category().trim()));
    }

    // RecipeService has no lookup by id, so find the recipe in the full list (small table).
    private Recipe requireRecipe(int id) {
        return findRecipe(recipeService, id);
    }

    static Recipe findRecipe(RecipeService recipeService, int id) {
        return recipeService.allRecipes().stream()
                .filter(recipe -> recipe.getId() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No recipe with id " + id));
    }
}
