package de.dahaem.api;

/**
 * JSON body for every error response: the HTTP status, its reason phrase, a readable (English) message and a stable
 * machine-readable code (for example NOT_FOUND, BAD_REQUEST, PAST_DAY, PAST_SHOPPING_DAY, INVALID_BARCODE, BARCODE_CHECKSUM, LIST_COMPLETED, CONFLICT, LOGIN_REQUIRED,
 * CSRF_INVALID, BAD_CREDENTIALS, INVALID_TOKEN) that a client can map to its own text.
 */
public record ErrorResponse(int status, String error, String message, String code) {
}
