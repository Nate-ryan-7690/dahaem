package de.dahaem.stores;

/**
 * A brand name DAHÄM does not have, where creating a new brand was not asked for. Stops a typo from adding a bogus
 * brand. A bad request (400) with its own code, UNKNOWN_BRAND.
 */
public class UnknownBrandException extends IllegalArgumentException {

    public UnknownBrandException(String name) {
        super("There is no brand called '" + name + "'. Pick an existing brand, or ask explicitly to create a new one");
    }
}
