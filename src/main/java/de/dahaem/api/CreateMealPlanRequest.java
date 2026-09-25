package de.dahaem.api;

import java.time.LocalDate;

/** Body of POST /api/meal-plans, e.g. {@code {"weekStartDate": "2026-09-28"}}. */
public record CreateMealPlanRequest(LocalDate weekStartDate) {
}
