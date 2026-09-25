package de.dahaem.api;

/** Thrown by a controller when the requested resource does not exist; mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
