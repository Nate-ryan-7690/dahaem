package de.dahaem.shopping;

import java.time.LocalDate;

/** One saved list of a meal plan in short: when it was made, for which shopping day, from which meal day, how many lines. */
public record ShoppingListSummary(int id, LocalDate createdDate, LocalDate shoppingDate, LocalDate fromDate,
                                  boolean status, int lines) {
}
