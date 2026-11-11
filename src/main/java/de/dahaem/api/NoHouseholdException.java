package de.dahaem.api;

/** The acting user belongs to no household, so there is no data to work on. A 403 with its own code, NO_HOUSEHOLD. */
public class NoHouseholdException extends RuntimeException {

    public NoHouseholdException() {
        super("This account belongs to no household");
    }
}
