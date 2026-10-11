package de.dahaem.api;

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

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final TagService tagService;

    public RecipeController(RecipeService recipeService, TagService tagService) {
        this.recipeService = recipeService;
        this.tagService = tagService;
    }

    @GetMapping
    public List<RecipeSummaryDto> all() {
        return recipeService.allRecipes().stream().map(RecipeSummaryDto::from).toList();
    }

    /**
     * The recipe with its ingredients and instructions. An optional {@code ?people=N} adds each
     * ingredient's quantity scaled by {@code people / servings} as {@code scaledQuantity}; the stored
     * {@code quantity}, the instructions and the nutrition are never changed. An optional
     * {@code ?basis=max} scales a "3 – 4 Personen" range recipe from its upper bound
     * ({@code people / servingsMax}) instead; it is ignored for a recipe that is not a range, and
     * {@code ?basis=servings} is the default.
     */
    @GetMapping("/{id}")
    public RecipeDetailDto byId(@PathVariable("id") int id,
                                @RequestParam(name = "people", required = false) Integer people,
                                @RequestParam(name = "basis", required = false) String basis) {
        if (people != null && !Portions.isValidPeople(people)) {
            throw new BadRequestException(Portions.INVALID_PEOPLE_MESSAGE);
        }
        if (basis != null && !basis.equals(RecipeDetailDto.BASIS_SERVINGS) && !basis.equals(RecipeDetailDto.BASIS_MAX)) {
            throw new BadRequestException(Portions.INVALID_BASIS_MESSAGE);
        }
        Recipe recipe = requireRecipe(id);
        return RecipeDetailDto.from(recipe, recipeService.findAllIngredients(id), recipeService.findAllInstructions(id),
                people, RecipeDetailDto.BASIS_MAX.equals(basis));
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
