package de.dahaem.auth;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Checks an email and a password for the PHP front end, which owns the user's session: no session is started here, the
 * answer says who the user is. Attempts from one client address slow that address down ({@link LoginThrottle}). No SQL
 * here, no UI here.
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
     * @throws TooManyAttemptsException  while this address is being slowed down (the password is not even looked at)
     * @throws InvalidCredentialsException for a wrong password and for an unknown email alike
     */
    public Verified verify(String email, String rawPassword, String ip) {
        OptionalLong wait = throttle.begin(ip);   // counts the attempt now, before the slow password check
        if (wait.isPresent()) {
            throw new TooManyAttemptsException(wait.getAsLong());
        }
        Optional<AppUser> user = accounts.verifyPassword(email, rawPassword);
        if (user.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        throttle.succeeded(ip);
        return new Verified(user.get(), accounts.membershipFor(user.get().getId()).orElse(null));
    }
}
