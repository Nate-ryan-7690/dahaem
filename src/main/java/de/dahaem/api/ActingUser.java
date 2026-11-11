package de.dahaem.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The logged-in user on whose behalf the PHP front end calls: the id in the {@code X-Acting-User} header, believed only on
 * a request that carries the internal token (the PHP owns the session and is the only one that can say who is logged in);
 * without a configured token (never in production) it is taken as it comes.
 */
final class ActingUser {

    private ActingUser() {
    }

    /** The acting user's id; a missing, malformed or untrusted header is a BadRequestException (a bug in the caller). */
    static int require(HttpServletRequest request, InternalTokenFilter token) {
        String value = request.getHeader(AuditLog.ACTING_USER_HEADER);
        // Believed only with the internal token. Without a configured token (local development and tests, never in
        // production: see StartupGuard) nobody is authenticated by the token, so the header is taken as it comes.
        boolean trusted = token.hasValidToken(request) || !token.isConfigured();
        if (!trusted || value == null || !value.trim().matches("[0-9]{1,9}")) {
            throw new BadRequestException("The " + AuditLog.ACTING_USER_HEADER
                    + " header (the logged-in user's id) is required");
        }
        return Integer.parseInt(value.trim());
    }
}
