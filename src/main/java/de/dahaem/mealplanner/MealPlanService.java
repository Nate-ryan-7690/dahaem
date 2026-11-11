package de.dahaem.mealplanner;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for meal plans. No SQL here, no UI here.
 * It asks the repository for data and answers questions about it.
 * This is the class future web controllers will call.
 *
 * <p>Meal plans belong to a household. The injected service is not bound to one ({@link #today()} and
 * {@link #forHousehold} work, nothing else); {@code forHousehold(id)} gives the service for one household, and every
 * plan and meal it reads, creates, changes or removes is that household's. Another household's ids are simply not found.
 */

public class MealPlanService {
    /** The household's time zone: "today" (and so which days are past) is the date there, not the server's. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Berlin");

    private final MealPlanRepository repository;
    private final Clock clock;
    private final Integer householdId;   // null = not bound to a household

    public MealPlanService(MealPlanRepository repository) { this(repository, Clock.system(DEFAULT_ZONE)); }

    /** The clock decides what "today" is, so tests (and a configured time zone) control which days are past. */
    public MealPlanService(MealPlanRepository repository, Clock clock) {
        this(repository, clock, null);
    }

    private MealPlanService(MealPlanRepository repository, Clock clock, Integer householdId) {
        this.repository = repository;
        this.clock = clock;
        this.householdId = householdId;
    }

    /** The meal plans of one household: the same service, bound to that household. */
    public MealPlanService forHousehold(int householdId) {
        return new MealPlanService(repository, clock, householdId);
    }

    private int household() {
        if (householdId == null) {
            throw new IllegalStateException("This meal plan service is not bound to a household: use forHousehold(id)");
        }
        return householdId;
    }

    /** Today's date on the service's clock. */
    public LocalDate today() { return LocalDate.now(clock); }

    public int createWeeklyPlan(LocalDate weekStart) { return repository.create(household(), weekStart); }

    /** The plan with this id if it is the household's, else empty. */
    public Optional<MealPlan> findPlan(int planId) { return repository.findPlan(household(), planId); }

    public void addRecipe(int planId, int recipeId, String mealTime) {
        repository.addRecipe(planId, recipeId, mealTime);
    }

    /** Removes a recipe from a plan. Returns true if the recipe was on the plan. */
    public boolean removeRecipe(int planId, int recipeId) {
        return repository.removeRecipe(planId, recipeId);
    }

    public List<MealPlanRecipe> recipesForPlan(int planId) { return repository.findRecipesForPlan(planId); }

    public List<MealPlan> allPlans() { return repository.findAll(household()); }

    // ---------- planned meals by date: any day, any number of meals, each addressed by its own entry id ----------

    /** One day of a week with its meals in order. */
    public record Day(LocalDate date, List<PlannedMeal> meals) {
    }

    /** One Monday-to-Sunday week. {@code planId} is null while nothing has been planned for it yet. */
    public record Week(LocalDate weekStart, Integer planId, List<Day> days) {
    }

    /** The Monday of the week a day belongs to. */
    public static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * The week that contains {@code anyDay}: seven days, each with its meals, whether or not a plan exists yet
     * (a week with no plan is seven empty days; the first meal added creates the plan).
     */
    public Week week(LocalDate anyDay) {
        if (anyDay == null) {
            throw new IllegalArgumentException("A date is required");
        }
        LocalDate monday = mondayOf(anyDay);
        Optional<MealPlan> plan = repository.findPlanByWeekStart(household(), monday);
        List<PlannedMeal> meals = plan.map(p -> repository.findMeals(p.getId())).orElse(List.of());
        List<Day> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            days.add(new Day(date, meals.stream().filter(m -> date.equals(m.getDate())).toList()));
        }
        return new Week(monday, plan.map(MealPlan::getId).orElse(null), days);
    }

    /**
     * Plans a recipe on a day. The plan of that day's week is created if it does not exist yet. Without a
     * position the meal goes last on its day. servings null = follow the default (see {@link MealServings}).
     *
     * @throws IllegalArgumentException for a missing date or a servings / position below its minimum
     * @throws PastDayException         when the day is before today
     */
    public PlannedMeal addMeal(int recipeId, LocalDate date, String label, Integer servings, String note,
                               Integer position) {
        if (date == null) {
            throw new IllegalArgumentException("'date' is required (YYYY-MM-DD)");
        }
        requireServings(servings);
        requirePosition(position);
        requireNotPast(date);
        int planId = planIdForWeek(mondayOf(date));
        int at = position != null ? position : repository.nextPosition(planId, date);
        int entryId = repository.insertMeal(planId, recipeId, date, cleanText(label), servings, at, cleanText(note));
        return repository.findMeal(household(), entryId)
                .orElseThrow(() -> new IllegalStateException("Planned meal " + entryId + " vanished after insert"));
    }

    /**
     * Edits or moves one planned meal. Fields not named in the change stay as they are. Moving a meal to a day
     * in another week moves it to that week's plan (created if needed); moved to another day without a position
     * it goes last on the new day.
     *
     * @return the meal as it is now, or empty when there is no such meal
     * @throws IllegalArgumentException for a servings / position below its minimum or an empty change
     * @throws PastDayException         when the meal is on a past day or would be moved to one
     */
    public Optional<PlannedMeal> updateMeal(int entryId, MealChange change) {
        if (change.isEmpty()) {
            throw new IllegalArgumentException("Nothing to change: send at least one field");
        }
        requireServings(change.servings());
        requirePosition(change.position());
        Optional<PlannedMeal> found = repository.findMeal(household(), entryId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PlannedMeal meal = found.get();
        requireNotPast(meal.getDate());
        if (change.date() != null) {
            requireNotPast(change.date());
        }
        LocalDate date = change.date() != null ? change.date() : meal.getDate();
        int planId = meal.getPlanId();
        if (!mondayOf(date).equals(mondayOf(meal.getDate()))) {
            planId = planIdForWeek(mondayOf(date));
        }
        int position = meal.getPosition();
        if (change.position() != null) {
            position = change.position();
        } else if (!date.equals(meal.getDate())) {
            position = repository.nextPosition(planId, date);
        }
        repository.updateMeal(household(), entryId, planId,
                change.recipeId() != null ? change.recipeId() : meal.getRecipeId(),
                date,
                change.setLabel() ? cleanText(change.label()) : meal.getLabel(),
                change.setServings() ? change.servings() : meal.getOwnServings(),
                position,
                change.setNote() ? cleanText(change.note()) : meal.getNote());
        return repository.findMeal(household(), entryId);
    }

    /**
     * Removes one planned meal by its own id. Returns true if it existed.
     *
     * @throws PastDayException when the meal is on a past day
     */
    public boolean removeMeal(int entryId) {
        Optional<PlannedMeal> found = repository.findMeal(household(), entryId);
        if (found.isEmpty()) {
            return false;
        }
        requireNotPast(found.get().getDate());
        return repository.deleteMeal(entryId);
    }

    /**
     * The nightly job: writes the resolved servings onto every meal of a past day that still follows the default,
     * so a later change of the household number or of a recipe's preference no longer rewrites history. The number
     * is worked out by {@link MealServings} (recipe preference, then {@code householdPeople}, then 2) and stored as
     * the meal's own. Meals that already have a number of their own and meals of today or later are left alone,
     * and running it again freezes nothing new. {@code householdPeople} may be null (not set).
     *
     * @return how many meals were frozen
     */
    public int freezePastServings(Integer householdPeople) {
        int frozen = 0;
        for (PlannedMeal meal : repository.findUnfrozenMealsBefore(household(), today())) {
            if (repository.freezeServings(meal.getEntryId(), MealServings.resolve(meal, householdPeople).servings())) {
                frozen++;
            }
        }
        return frozen;
    }

    public Optional<PlannedMeal> findMeal(int entryId) {
        return repository.findMeal(household(), entryId);
    }

    // The plan of a week of this household: the existing one, or a new one. Two requests creating the same week at once
    // hit the UNIQUE (household, week); the loser looks the winner's plan up instead of failing.
    private int planIdForWeek(LocalDate monday) {
        Optional<MealPlan> existing = repository.findPlanByWeekStart(household(), monday);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        try {
            return repository.create(household(), monday);
        } catch (RuntimeException e) {
            return repository.findPlanByWeekStart(household(), monday).map(MealPlan::getId).orElseThrow(() -> e);
        }
    }

    private void requireNotPast(LocalDate day) {
        LocalDate today = today();
        if (day.isBefore(today)) {
            throw new PastDayException(day, today);
        }
    }

    private static void requireServings(Integer servings) {
        if (servings != null && servings < 1) {
            throw new IllegalArgumentException("'servings' must be at least 1 (or null to follow the default)");
        }
    }

    private static void requirePosition(Integer position) {
        if (position != null && position < 0) {
            throw new IllegalArgumentException("'position' must be 0 or more");
        }
    }

    // Trims; blank means none.
    private static String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}
