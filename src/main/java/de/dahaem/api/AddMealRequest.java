package de.dahaem.api;

import java.time.LocalDate;

/**
 * Body of POST /api/meal-plans/meals, e.g.
 * {@code {"recipeId": 3, "date": "2026-11-09", "label": "Abendessen", "servings": 4, "note": "Reste", "position": 0}}.
 * Only recipeId and date are required. servings null = follow the default; position null = last on its day.
 */
public record AddMealRequest(Integer recipeId, LocalDate date, String label, Integer servings, String note,
                             Integer position) {
}
