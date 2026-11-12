package de.dahaem.api;

/** An admin tried to remove themselves from their household. A conflict (409) with its own code, CANNOT_REMOVE_SELF. */
public class CannotRemoveSelfException extends RuntimeException {

    public CannotRemoveSelfException() {
        super("You cannot remove yourself from the household");
    }
}
