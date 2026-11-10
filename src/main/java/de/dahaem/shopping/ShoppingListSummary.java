package de.dahaem.shopping;

import java.time.LocalDate;

/**
 * One saved list in short: when it was made, for which shopping day, from which meal day, how many lines and how many of
 * them are ticked as bought ("3 von 8 gekauft").
 */
public record ShoppingListSummary(int id, Integer mealPlanId, LocalDate createdDate, LocalDate shoppingDate, LocalDate fromDate,
                                  boolean status, boolean archived, int lines, int purchasedLines) {
}
