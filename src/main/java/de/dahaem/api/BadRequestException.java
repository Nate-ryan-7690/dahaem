package de.dahaem.api;

/** Thrown by a controller when request input fails validation; mapped to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
