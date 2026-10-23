package de.dahaem.api;

import de.dahaem.diet.DietFilterService;
import de.dahaem.diet.DietRules;
import de.dahaem.mealplanner.Recipe;
import de.dahaem.mealplanner.RecipeQuery;
import de.dahaem.mealplanner.RecipeSearchService;
import de.dahaem.mealplanner.RecipeService;
import de.dahaem.shared.PageRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final TagService tagService;
    private final DietFilterService dietFilter;
    private final RecipeSearchService searchService;

    public RecipeController(RecipeService recipeService, TagService tagService, DietFilterService dietFilter,
                            RecipeSearchService searchService) {
        this.searchService = searchService;
        this.recipeService = recipeService;
        this.tagService = tagService;
        this.dietFilter = dietFilter;
    }

    /**
     * Search the recipes: one page of cards plus the total. Everything is applied on the server (the client never
     * filters 11,000 recipes). All filters are optional and combine with AND:
     * <ul>
     *   <li>search: {@code q=<words>} (every word is in the recipe name), {@code ingredient=a,b} (repeatable; the
     *       recipe has an ingredient whose name contains each text);</li>
     *   <li>filters: {@code tags=a,b} (repeatable; the recipe carries all of these tags), {@code minTime} /
     *       {@code maxTime} (total time = prep + cook, minutes; recipes without a time are left out when either is
     *       set), {@code servings=N} (recipes for N people; a "3 – 4 Personen" recipe matches 3 and 4);</li>
     *   <li>order: {@code sort=name|time|rating|newest} (default name; ties always end by id, so the order is stable
     *       and paging neither repeats nor skips a recipe);</li>
     *   <li>paging: {@code page} (from 1, default 1) and {@code pageSize} (1 - 100, default 24). Without paging
     *       parameters you get the first 24 recipes, not all of them; use {@code total} / {@code totalPages} to go
     *       on. A page past the end is empty. Out-of-range values are a 400.</li>
     * </ul>
     * Optional diet rules, also applied on the server:
     * <ul>
     *   <li>hard rules ("Was esse ich nicht?"): {@code diet=<preset>} (repeatable; halal, koscher, vegetarisch,
     *       vegan, see config/diet_presets.json; an unknown preset is a 400), {@code excludeTags=a,b},
     *       {@code excludeItems=<item ids>};</li>
     *   <li>soft rules ("Was esse ich lieber nicht?"): {@code avoidTags}, {@code avoidItems}: recipes stay, are
     *       sorted after the others and list what matched in {@code diet.softHits};</li>
     *   <li>{@code includeUnclear=true|false} (default false): recipes with a line that was not matched are
     *       UNCLEAR under a hard rule and left out unless this is true.</li>
     * </ul>
     * With rules every card carries {@code diet} (status OK / ANPASSBAR / UNCLEAR, hints, softHits); HIDDEN recipes
     * are not returned and not counted in {@code total}. With rules the order is: no soft hit first, then OK before
     * ANPASSBAR before UNCLEAR, then the requested sort. The rules judge from a snapshot of the recipe facts that is
     * at most a minute old; {@code GET /api/recipes/{id}} always judges fresh. The presets filter INGREDIENTS only;
     * they are no halal or kosher certification: meat certification stays the shopper's responsibility.
     */
    @GetMapping
    public RecipePageDto search(@RequestParam(name = "q", required = false) String q,
                                @RequestParam(name = "ingredient", required = false) List<String> ingredient,
                                @RequestParam(name = "tags", required = false) List<String> tags,
                                @RequestParam(name = "minTime", required = false) Integer minTime,
                                @RequestParam(name = "maxTime", required = false) Integer maxTime,
                                @RequestParam(name = "servings", required = false) Integer servings,
                                @RequestParam(name = "sort", required = false) String sort,
                                @RequestParam(name = "page", required = false) Integer page,
                                @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                @RequestParam(name = "diet", required = false) List<String> diet,
                                @RequestParam(name = "excludeTags", required = false) List<String> excludeTags,
                                @RequestParam(name = "excludeItems", required = false) List<Integer> excludeItems,
                                @RequestParam(name = "avoidTags", required = false) List<String> avoidTags,
                                @RequestParam(name = "avoidItems", required = false) List<Integer> avoidItems,
                                @RequestParam(name = "includeUnclear", defaultValue = "false") boolean includeUnclear) {
        RecipeQuery query = RecipeQuery.of(q, ingredient, tags, minTime, maxTime, servings, sort);
        PageRequest request = PageRequest.of(page, pageSize);
        DietRules rules = dietFilter.rules(diet, excludeTags, excludeItems, avoidTags, avoidItems, includeUnclear);
        RecipeSearchService.Result result = searchService.search(query, rules, request);
        List<RecipeCardDto> items = result.items().stream()
                .map(e -> RecipeCardDto.from(e.recipe(), e.tags(),
                        e.diet() == null ? null : DietDto.from(e.diet(), null)))
                .toList();
        return new RecipePageDto(items, result.total(), request.page(), request.pageSize(), result.totalPages());
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
        TagDto tag = TagDto.from(tagService.addTagToRecipe(id, request.name().trim(), request.category().trim()));
        dietFilter.invalidate();   // the diet snapshot holds the tag names
        return tag;
    }

    private Recipe requireRecipe(int id) {
        return findRecipe(recipeService, id);
    }

    static Recipe findRecipe(RecipeService recipeService, int id) {
        return recipeService.findById(id)
                .orElseThrow(() -> new NotFoundException("No recipe with id " + id));
    }
}
