package de.dahaem.shopping;

/** A completed shopping list cannot be changed (reopen it first). */
public class ListCompletedException extends RuntimeException {

    public ListCompletedException(int listId) {
        super("Shopping list " + listId + " is completed and cannot be changed; reopen it first");
    }
}
