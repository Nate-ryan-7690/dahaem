package de.dahaem.api;

import de.dahaem.mealplanner.MealPlanService;

import java.time.LocalDate;
import java.util.List;

/**
 * One Monday-to-Sunday week: always seven days, each with its meals in order. {@code planId} is null while
 * nothing has been planned for the week (the first meal added creates the plan). {@code householdPeople} is the
 * household head count that was used to resolve the servings (null when no household was named or none is set).
 */
public record MealPlanWeekDto(LocalDate weekStart, Integer planId, Integer householdPeople, List<DayDto> days) {

    public record DayDto(LocalDate date, List<PlannedMealDto> meals) {
    }

    static MealPlanWeekDto from(MealPlanService.Week week, Integer householdPeople) {
        return new MealPlanWeekDto(week.weekStart(), week.planId(), householdPeople,
                week.days().stream()
                        .map(day -> new DayDto(day.date(),
                                day.meals().stream().map(m -> PlannedMealDto.from(m, householdPeople)).toList()))
                        .toList());
    }
}
