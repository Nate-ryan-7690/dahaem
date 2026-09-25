package de.dahaem.api;

import de.dahaem.mealplanner.RecipeService;
import de.dahaem.shopping.ShoppingListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shopping-list")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;
    private final RecipeService recipeService;

    public ShoppingListController(ShoppingListService shoppingListService, RecipeService recipeService) {
        this.shoppingListService = shoppingListService;
        this.recipeService = recipeService;
    }

    /** Builds (does not save) a shopping list for {@code ?recipeIds=1,2,3} or {@code ?recipeIds=1&recipeIds=2}. */
    @GetMapping
    public ShoppingListDto build(@RequestParam(name = "recipeIds") List<Integer> recipeIds) {
        List<Integer> ids = recipeIds.stream().distinct().toList();
        if (ids.isEmpty() || ids.contains(null)) {
            throw new BadRequestException("'recipeIds' must list at least one recipe id");
        }
        ids.forEach(id -> RecipeController.findRecipe(recipeService, id));
        return ShoppingListDto.from(shoppingListService.buildFromRecipes(ids));
    }
}
