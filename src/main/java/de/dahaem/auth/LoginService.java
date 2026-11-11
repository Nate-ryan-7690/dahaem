package de.dahaem.auth;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Checks an email and a password for the PHP front end, which owns the user's session: no session is started here, the
 * answer says who the user is. Failed attempts slow the caller down ({@link LoginThrottle}). No SQL here, no UI here.
 */
public class LoginService {

    /** The user and their household membership (null if in no household). */
    public record Verified(AppUser user, HouseholdMembership membership) {
    }

    private final AccountService accounts;
    private final LoginThrottle throttle;

    public LoginService(AccountService accounts, LoginThrottle throttle) {
        this.accounts = accounts;
        this.throttle = throttle;
    }

    /**
     * @param ip the client address (for the throttle)
     * @throws TooManyAttemptsException  while this address or email is being slowed down (the password is not even looked at)
     * @throws InvalidCredentialsException for a wrong password and for an unknown email alike
     */
    public Verified verify(String email, String rawPassword, String ip) {
        String normalized = AccountService.normalizeEmail(email);
        OptionalLong wait = throttle.retryAfterSeconds(ip, normalized);
        if (wait.isPresent()) {
            throw new TooManyAttemptsException(wait.getAsLong());
        }
        Optional<AppUser> user = accounts.verifyPassword(email, rawPassword);
        if (user.isEmpty()) {
            throttle.failure(ip, normalized);
            throw new InvalidCredentialsException();
        }
        throttle.success(normalized);
        return new Verified(user.get(), accounts.membershipFor(user.get().getId()).orElse(null));
    }
}
