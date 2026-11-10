package de.dahaem.shopping;

import java.time.LocalDate;

/**
 * Raw shopping_list row, before its items are attached. shoppingDate (the day of the trip, offers are judged on it) and
 * fromDate (the first meal day the list counts) are null on a list made before migration 0017.
 */
public record ShoppingListRow(int id, Integer mealPlanId, LocalDate createdDate, LocalDate shoppingDate,
                              LocalDate fromDate, boolean status) {
}
