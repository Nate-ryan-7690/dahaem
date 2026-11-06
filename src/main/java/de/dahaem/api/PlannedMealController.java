package de.dahaem.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.dahaem.auth.AccountService;
import de.dahaem.mealplanner.MealChange;
import de.dahaem.mealplanner.MealPlanService;
import de.dahaem.mealplanner.PlannedMeal;
import de.dahaem.mealplanner.RecipeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The meal planner by date: read a week, plan a recipe on a day (as often as wanted), edit or move a planned meal,
 * remove it, and set a recipe's preferred servings. Every meal is addressed by its own {@code entryId}. A plan is
 * one Monday-to-Sunday week; the first meal added to a week creates its plan, so a page never has to create one.
 *
 * <p>The household is named with an optional {@code householdId} so the servings can follow its head count; the
 * PHP decides which household an account may use (see the backlog item "Auth and session review"). Without it,
 * a meal that has no number of its own and no recipe preference is cooked for 2.
 */
@RestController
@RequestMapping("/api")
public class PlannedMealController {

    private final MealPlanService mealPlanService;
    private final RecipeService recipeService;
    private final AccountService accountService;

    public PlannedMealController(MealPlanService mealPlanService, RecipeService recipeService,
                                 AccountService accountService) {
        this.mealPlanService = mealPlanService;
        this.recipeService = recipeService;
        this.accountService = accountService;
    }

    /**
     * The week that contains {@code date} (today when omitted): seven days with their meals. A week with no plan
     * is seven empty days and {@code planId: null}. Any day of the week works; the answer starts on its Monday.
     */
    @GetMapping("/meal-plans/week")
    public MealPlanWeekDto week(@RequestParam(name = "date", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(name = "householdId", required = false) Integer householdId) {
        Integer people = householdPeople(householdId);
        return MealPlanWeekDto.from(mealPlanService.week(date != null ? date : LocalDate.now()), people);
    }

    /** Plans a recipe on a day and returns the meal. The same recipe may be planned many times. */
    @PostMapping("/meal-plans/meals")
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedMealDto addMeal(@RequestBody AddMealRequest request,
                                  @RequestParam(name = "householdId", required = false) Integer householdId) {
        if (request.recipeId() == null) {
            throw new BadRequestException("'recipeId' is required");
        }
        if (request.date() == null) {
            throw new BadRequestException("'date' is required (YYYY-MM-DD)");
        }
        Integer people = householdPeople(householdId);
        RecipeController.findRecipe(recipeService, request.recipeId());
        PlannedMeal meal = mealPlanService.addMeal(request.recipeId(), request.date(), request.label(),
                request.servings(), request.note(), request.position());
        return PlannedMealDto.from(meal, people);
    }

    /**
     * Edits or moves a meal. Send only what changes: {@code date} (moves it, also into another week),
     * {@code position}, {@code label}, {@code note}, {@code servings}, {@code recipeId}. {@code servings: null}
     * (or {@code label} / {@code note}: null) clears it, so the meal follows the default again.
     */
    @PatchMapping("/meal-plans/meals/{entryId}")
    public PlannedMealDto editMeal(@PathVariable("entryId") int entryId, @RequestBody JsonNode body,
                                   @RequestParam(name = "householdId", required = false) Integer householdId) {
        Integer people = householdPeople(householdId);
        MealChange change = toChange(body);
        if (change.recipeId() != null) {
            RecipeController.findRecipe(recipeService, change.recipeId());
        }
        PlannedMeal meal = mealPlanService.updateMeal(entryId, change)
                .orElseThrow(() -> new NotFoundException("No planned meal with id " + entryId));
        return PlannedMealDto.from(meal, people);
    }

    /** Removes one planned meal by its own id. 404 when there is none. */
    @DeleteMapping("/meal-plans/meals/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMeal(@PathVariable("entryId") int entryId) {
        if (!mealPlanService.removeMeal(entryId)) {
            throw new NotFoundException("No planned meal with id " + entryId);
        }
    }

    /**
     * Sets how many people this recipe is always cooked for, e.g. {@code {"servings": 4}}. {@code {"servings": null}}
     * clears it. It never changes the number the stored amounts are written for.
     */
    @PutMapping("/recipes/{id}/preferred-servings")
    public PreferredServingsDto setPreferredServings(@PathVariable("id") int id, @RequestBody JsonNode body) {
        if (!body.isObject() || !body.has("servings")) {
            throw new BadRequestException("'servings' is required (a whole number, at least 1, or null to clear it)");
        }
        Integer servings = nullableInt(body, "servings");
        if (!recipeService.setPreferredServings(id, servings)) {
            throw new NotFoundException("No recipe with id " + id);
        }
        return new PreferredServingsDto(id, servings);
    }

    private Integer householdPeople(Integer householdId) {
        if (householdId == null) {
            return null;
        }
        return accountService.findHousehold(householdId)
                .orElseThrow(() -> new NotFoundException("No household with id " + householdId))
                .getPeople();
    }

    private static MealChange toChange(JsonNode body) {
        if (!body.isObject()) {
            throw new BadRequestException("The body must be a JSON object");
        }
        MealChange change = new MealChange();
        if (body.has("recipeId")) {
            Integer recipeId = nullableInt(body, "recipeId");
            if (recipeId == null) {
                throw new BadRequestException("'recipeId' cannot be null");
            }
            change.withRecipeId(recipeId);
        }
        if (body.has("date")) {
            JsonNode date = body.get("date");
            if (!date.isTextual()) {
                throw new BadRequestException("'date' must be a date (YYYY-MM-DD)");
            }
            try {
                change.withDate(LocalDate.parse(date.asText()));
            } catch (DateTimeParseException e) {
                throw new BadRequestException("'date' must be a date (YYYY-MM-DD)");
            }
        }
        if (body.has("position")) {
            Integer position = nullableInt(body, "position");
            if (position == null) {
                throw new BadRequestException("'position' cannot be null");
            }
            change.withPosition(position);
        }
        if (body.has("servings")) {
            change.withServings(nullableInt(body, "servings"));
        }
        if (body.has("label")) {
            change.withLabel(nullableText(body, "label"));
        }
        if (body.has("note")) {
            change.withNote(nullableText(body, "note"));
        }
        return change;
    }

    private static Integer nullableInt(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new BadRequestException("'" + field + "' must be a whole number");
        }
        return node.intValue();
    }

    private static String nullableText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new BadRequestException("'" + field + "' must be text");
        }
        return node.asText();
    }
}
