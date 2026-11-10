package de.dahaem.api;

import de.dahaem.auth.AccountService;
import de.dahaem.mealplanner.MealPlan;
import de.dahaem.mealplanner.MealPlanService;
import de.dahaem.mealplanner.RecipeService;
import de.dahaem.shared.Portions;
import de.dahaem.shopping.ShoppingListService;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final AccountService accountService;

    public MealPlanController(MealPlanService mealPlanService, RecipeService recipeService,
                               ShoppingListService shoppingListService, AccountService accountService) {
        this.mealPlanService = mealPlanService;
        this.recipeService = recipeService;
        this.shoppingListService = shoppingListService;
        this.accountService = accountService;
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
     * Builds and persists a shopping list from a plan's meals: one line per item still missing after
     * subtracting inventory, no store item pre-chosen. Returns the new list's id.
     *
     * <p>By default every planned meal is scaled by its own servings (the meal's own number, then its recipe's
     * preferred servings, then the household head count, then 2; see
     * {@link ShoppingListService#generateFromMealPlanByServings}), so a recipe planned twice counts twice. The
     * optional {@code ?householdId=N} lets the head count apply. An explicit {@code ?people=N} overrides all of
     * that: every recipe is scaled to N people, as before.
     *
     * <p>{@code ?shoppingDate=YYYY-MM-DD} is the day of the shopping trip (default today, not in the past): the
     * offers that run on that day are the ones shown, so a list made today for a trip next week shows next week's
     * offers. {@code ?from=YYYY-MM-DD} is the first meal day the list counts (default the shopping day; pass the next
     * day when the shopping is after dinner). Every call makes a NEW list; earlier lists of the plan stay as they
     * were ({@code GET /{id}/shopping-lists} shows them). Stock is subtracted only in the same unit and only when it
     * has not expired by the shopping day; a unit mismatch leaves the line flagged {@code needsReview}.
     */
    @PostMapping("/{id}/shopping-list")
    @ResponseStatus(HttpStatus.CREATED)
    public GeneratedShoppingListDto generateShoppingList(@PathVariable("id") int id,
                                                          @RequestParam(name = "people", required = false) Integer people,
                                                          @RequestParam(name = "householdId", required = false) Integer householdId,
                                                          @RequestParam(name = "shoppingDate", required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shoppingDate,
                                                          @RequestParam(name = "from", required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        mealPlanService.allPlans().stream()
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No meal plan with id " + id));
        if (people != null && !Portions.isValidPeople(people)) {
            throw new BadRequestException(Portions.INVALID_PEOPLE_MESSAGE);
        }
        LocalDate today = mealPlanService.today();   // the household's day, not the server's
        LocalDate tripDay = shoppingDate != null ? shoppingDate : today;
        LocalDate firstMealDay = from != null ? from : tripDay;
        int shoppingListId;
        if (people != null) {
            shoppingListId = shoppingListService.generateFromMealPlan(id, today, people, tripDay, firstMealDay);
        } else {
            Integer householdPeople = householdId == null ? null : accountService.findHousehold(householdId)
                    .orElseThrow(() -> new NotFoundException("No household with id " + householdId))
                    .getPeople();
            shoppingListId = shoppingListService.generateFromMealPlanByServings(id, today, householdPeople, tripDay,
                    firstMealDay);
        }
        return new GeneratedShoppingListDto(shoppingListId);
    }

    /**
     * The saved shopping lists of a plan, newest first (each generation is its own list; none is ever rewritten).
     * Archived lists are left out; {@code ?archived=true} shows only the archived ones.
     */
    @GetMapping("/{id}/shopping-lists")
    public List<PersistedShoppingListDto.Summary> shoppingLists(@PathVariable("id") int id,
            @RequestParam(name = "archived", defaultValue = "false") boolean archived) {
        mealPlanService.allPlans().stream()
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No meal plan with id " + id));
        return shoppingListService.listsForPlan(id, archived).stream().map(PersistedShoppingListDto.Summary::from).toList();
    }
}
