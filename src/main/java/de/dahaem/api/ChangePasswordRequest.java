package de.dahaem.api;

/** Body of PUT /api/account/password. */
public record ChangePasswordRequest(String currentPassword, String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";   // never let a log line carry a password
    }
}
