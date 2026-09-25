package de.dahaem.api;

import de.dahaem.mealplanner.Recipe;
import de.dahaem.mealplanner.RecipeService;
import de.dahaem.tags.TagService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/{id}")
    public RecipeDetailDto byId(@PathVariable("id") int id) {
        Recipe recipe = requireRecipe(id);
        return RecipeDetailDto.from(recipe, recipeService.findAllIngredients(id), recipeService.findAllInstructions(id));
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
