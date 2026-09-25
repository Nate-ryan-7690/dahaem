package de.dahaem.api;

/** Thrown by a controller when the request is not (or no longer) tied to a valid login; mapped to 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
