package de.dahaem.auth;

/** The household already has the most open invitations it may have. A conflict (409) with its own code, INVITATION_LIMIT. */
public class InvitationLimitException extends RuntimeException {

    public InvitationLimitException(int limit) {
        super("There are already " + limit + " open invitations: revoke one first");
    }
}
