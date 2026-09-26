package de.dahaem.api;

import de.dahaem.mealplanner.MealPlan;
import de.dahaem.mealplanner.MealPlanService;
import de.dahaem.mealplanner.RecipeService;
import de.dahaem.shopping.ShoppingListService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/meal-plans")
public class MealPlanController {

    private final MealPlanService mealPlanService;
    private final RecipeService recipeService;
    private final ShoppingListService shoppingListService;

    public MealPlanController(MealPlanService mealPlanService, RecipeService recipeService,
                               ShoppingListService shoppingListService) {
        this.mealPlanService = mealPlanService;
        this.recipeService = recipeService;
        this.shoppingListService = shoppingListService;
    }

    @GetMapping
    public List<MealPlanDto> all() {
        return mealPlanService.allPlans().stream()
                .map(plan -> MealPlanDto.from(plan, mealPlanService.recipesForPlan(plan.getId())))
                .toList();
    }

    /** Creates an empty plan for the week. A second plan for the same week is rejected with 409. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MealPlanDto create(@RequestBody CreateMealPlanRequest request) {
        if (request.weekStartDate() == null) {
            throw new BadRequestException("'weekStartDate' is required (YYYY-MM-DD)");
        }
        int id = mealPlanService.createWeeklyPlan(request.weekStartDate());
        return new MealPlanDto(id, request.weekStartDate(), List.of());
    }

    /** Adds a recipe to a plan and returns the updated plan. The same recipe twice on one plan is a 409. */
    @PostMapping("/{id}/recipes")
    @ResponseStatus(HttpStatus.CREATED)
    public MealPlanDto addRecipe(@PathVariable("id") int id, @RequestBody AddPlannedRecipeRequest request) {
        MealPlan plan = mealPlanService.allPlans().stream()
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No meal plan with id " + id));
        if (request.recipeId() == null) {
            throw new BadRequestException("'recipeId' is required");
        }
        if (request.mealTime() == null || request.mealTime().isBlank()) {
            throw new BadRequestException("'mealTime' is required");
        }
        RecipeController.findRecipe(recipeService, request.recipeId());
        mealPlanService.addRecipe(id, request.recipeId(), request.mealTime().trim());
        return MealPlanDto.from(plan, mealPlanService.recipesForPlan(id));
    }

    /** Removes a recipe from a plan and returns the updated plan. 404 if the plan or that recipe isn't found. */
    @DeleteMapping("/{id}/recipes/{recipeId}")
    public MealPlanDto removeRecipe(@PathVariable("id") int id, @PathVariable("recipeId") int recipeId) {
        MealPlan plan = mealPlanService.allPlans().stream()
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No meal plan with id " + id));
        if (!mealPlanService.removeRecipe(id, recipeId)) {
            throw new NotFoundException("Recipe " + recipeId + " is not on meal plan " + id);
        }
        return MealPlanDto.from(plan, mealPlanService.recipesForPlan(id));
    }

    /**
     * Builds and persists a shopping list from a plan's recipes (see
     * {@link ShoppingListService#generateFromMealPlan(int)}): one line per item still missing after
     * subtracting inventory, no store item pre-chosen. Returns the new list's id. An optional
     * {@code ?people=N} scales each recipe's ingredient quantities by {@code people / recipe_servings}
     * before summing; omitted, the raw per-recipe quantities are summed.
     */
    @PostMapping("/{id}/shopping-list")
    @ResponseStatus(HttpStatus.CREATED)
    public GeneratedShoppingListDto generateShoppingList(@PathVariable("id") int id,
                                                          @RequestParam(name = "people", required = false) Integer people) {
        mealPlanService.allPlans().stream()
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No meal plan with id " + id));
        if (people != null && people < 1) {
            throw new BadRequestException("'people' must be at least 1");
        }
        int shoppingListId = people == null
                ? shoppingListService.generateFromMealPlan(id)
                : shoppingListService.generateFromMealPlan(id, LocalDate.now(), people);
        return new GeneratedShoppingListDto(shoppingListId);
    }
}
