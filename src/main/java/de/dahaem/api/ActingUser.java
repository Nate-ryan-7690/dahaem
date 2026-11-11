package de.dahaem.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The logged-in user on whose behalf the PHP front end calls: the id in the {@code X-Acting-User} header, believed only on
 * a request that carries the internal token (the PHP owns the session and is the only one that can say who is logged in).
 */
final class ActingUser {

    private ActingUser() {
    }

    /** The acting user's id; a missing, malformed or untrusted header is a BadRequestException (a bug in the caller). */
    static int require(HttpServletRequest request, InternalTokenFilter token) {
        String value = request.getHeader(AuditLog.ACTING_USER_HEADER);
        if (!token.hasValidToken(request) || value == null || !value.trim().matches("[0-9]{1,9}")) {
            throw new BadRequestException("The " + AuditLog.ACTING_USER_HEADER
                    + " header (the logged-in user's id) is required");
        }
        return Integer.parseInt(value.trim());
    }
}
