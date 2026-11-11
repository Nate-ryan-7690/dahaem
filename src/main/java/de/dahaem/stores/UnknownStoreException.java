package de.dahaem.stores;

/**
 * A store name DAHÄM does not have, where creating a new store was not asked for. Stops a typo from adding a bogus store
 * (which would show on the Home store deck). A bad request (400) with its own code, UNKNOWN_STORE.
 */
public class UnknownStoreException extends IllegalArgumentException {

    public UnknownStoreException(String name) {
        super("There is no store called '" + name + "'. Pick an existing store, or ask explicitly to create a new one");
    }
}
