package de.dahaem.auth;

import java.util.OptionalLong;

/**
 * What a logged-in user may do to their own account: change the email and the password. Both need the current password,
 * and a wrong one counts as a login attempt from that client address ({@link LoginThrottle}), so a stolen session cannot
 * be used to guess it. No SQL here, no UI here.
 */
public class AccountSelfService {

    private final AccountService accounts;
    private final LoginThrottle throttle;

    public AccountSelfService(AccountService accounts, LoginThrottle throttle) {
        this.accounts = accounts;
        this.throttle = throttle;
    }

    /**
     * @throws TooManyAttemptsException  while the client address is being slowed down
     * @throws InvalidCredentialsException if the current password is wrong
     * @throws IllegalArgumentException  for a new password that is too short, too long or the same as the old one
     */
    public void changePassword(AppUser user, String currentPassword, String newPassword, String ip) {
        checkCurrentPassword(user, currentPassword, ip);
        if (newPassword != null && newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("The new password must differ from the current one");
        }
        accounts.changePassword(user.getId(), newPassword);
    }

    /**
     * @throws TooManyAttemptsException  while the client address is being slowed down
     * @throws InvalidCredentialsException if the password is wrong
     * @throws IllegalArgumentException  for an invalid address
     * @throws EmailTakenException       if another account has the address
     * @return the user with the new email
     */
    public AppUser changeEmail(AppUser user, String password, String newEmail, String ip) {
        checkCurrentPassword(user, password, ip);
        return accounts.changeEmail(user.getId(), newEmail);
    }

    private void checkCurrentPassword(AppUser user, String password, String ip) {
        OptionalLong wait = throttle.begin(ip);   // counted before the slow password check, like a login
        if (wait.isPresent()) {
            throw new TooManyAttemptsException(wait.getAsLong());
        }
        if (accounts.verifyPassword(user.getEmail(), password).isEmpty()) {
            throw new InvalidCredentialsException();
        }
        throttle.succeeded(ip);
    }
}
