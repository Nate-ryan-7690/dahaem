package de.dahaem.api;

/** Body of PUT /api/account/email: the user's password and the new address. */
public record ChangeEmailRequest(String password, String email) {

    @Override
    public String toString() {
        return "ChangeEmailRequest[email=" + email + ", password=***]";
    }
}
