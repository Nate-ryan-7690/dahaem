package de.dahaem.auth;

/** The email address already belongs to another account. A conflict (409) with its own code, EMAIL_TAKEN. */
public class EmailTakenException extends IllegalStateException {

    public EmailTakenException() {
        super("That email address is already in use");
    }
}
