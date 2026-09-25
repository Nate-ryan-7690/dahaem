package de.dahaem.mealplanner;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for meal plans. No SQL here, no UI here.
 * It asks the repository for data and answers questions about it.
 * This is the class future web controllers will call.
 */

public class MealPlanService {
    private final MealPlanRepository repository;

    public MealPlanService(MealPlanRepository repository) { this.repository = repository; }

    public int createWeeklyPlan(LocalDate weekStart) { return repository.create(weekStart); }

    public void addRecipe(int planId, int recipeId, String mealTime) {
        repository.addRecipe(planId, recipeId, mealTime);
    }

    public List<MealPlanRecipe> recipesForPlan(int planId) { return repository.findRecipesForPlan(planId); }

    public List<MealPlan> allPlans() { return repository.findAll(); }
}
