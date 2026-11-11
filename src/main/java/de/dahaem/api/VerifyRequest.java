package de.dahaem.api;

/** Body of POST /api/auth/verify: the login the PHP front end received from the user. */
public record VerifyRequest(String email, String password) {

    @Override
    public String toString() {
        return "VerifyRequest[email=" + email + ", password=***]";   // never let a log line carry the password
    }
}
