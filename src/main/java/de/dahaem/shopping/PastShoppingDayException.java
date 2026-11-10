package de.dahaem.shopping;

/** The shopping day of a list is before today. A bad request (400) with its own code, PAST_SHOPPING_DAY. */
public class PastShoppingDayException extends IllegalArgumentException {

    public PastShoppingDayException() {
        super("'shoppingDate' must not be before today");
    }
}
