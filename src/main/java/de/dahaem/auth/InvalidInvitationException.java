package de.dahaem.auth;

/**
 * The invitation code is not usable: wrong, used, revoked or expired. One answer for all of them (a 400 with its own code,
 * INVALID_INVITATION), so nobody can find out what a guessed code once was.
 */
public class InvalidInvitationException extends RuntimeException {

    public InvalidInvitationException() {
        super("This invitation code is not valid (it may be wrong, used, revoked or expired)");
    }
}
