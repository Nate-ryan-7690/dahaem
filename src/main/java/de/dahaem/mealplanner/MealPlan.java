package de.dahaem.mealplanner;

import java.time.LocalDate;

/**
 * Plain data holder for one row of the meal_plan table.
 * No logic here on purpose just the fields, a constructor, and getters.
 */

public class MealPlan {

    private final int id;
    private final LocalDate weekStartDate;

    public MealPlan(int id, LocalDate weekStartDate) {
        this.id = id;
        this.weekStartDate = weekStartDate;
    }

    public int getId() { return id; }

    public LocalDate getWeekStartDate() { return weekStartDate; }

    @Override
    public String toString() {
        return "MealPlan{" +
                "id=" + id +
                ", weekStartDate=" + weekStartDate +
                "}";
    }
}
