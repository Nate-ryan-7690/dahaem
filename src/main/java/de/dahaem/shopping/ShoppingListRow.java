package de.dahaem.shopping;

import java.time.LocalDate;

/** Raw shopping_list row, before its items are attached. */
public record ShoppingListRow(int id, Integer mealPlanId, LocalDate createdDate, boolean status) {
}
