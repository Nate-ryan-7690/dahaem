package de.dahaem.api;

/** JSON body for every error response: the HTTP status, its reason phrase, and a readable message. */
public record ErrorResponse(int status, String error, String message) {
}
