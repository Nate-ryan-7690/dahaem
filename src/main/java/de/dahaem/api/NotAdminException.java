package de.dahaem.api;

/** The acting user is not an admin of their household, so they may not manage invitations. A 403 with its own code, NOT_ADMIN. */
public class NotAdminException extends RuntimeException {

    public NotAdminException() {
        super("Only an admin of the household may do this");
    }
}
