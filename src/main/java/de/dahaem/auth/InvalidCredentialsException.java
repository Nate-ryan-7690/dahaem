package de.dahaem.auth;

/** The email or the password is wrong. One exception for both on purpose, so an answer never says which. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Wrong email or password");
    }
}
