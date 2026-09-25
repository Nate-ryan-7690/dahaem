package de.dahaem.api;

/** A CSRF token and where to put it: in the {@code headerName} header (or the {@code parameterName} form field). */
public record CsrfTokenDto(String headerName, String parameterName, String token) {
}
