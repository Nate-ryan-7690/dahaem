package de.dahaem.api;

import de.dahaem.auth.AccountSelfService;
import de.dahaem.auth.AccountService;
import de.dahaem.auth.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A logged-in user's own account. The PHP front end says who is logged in with the {@code X-Acting-User} header (the
 * user id from {@code POST /api/auth/verify}); every call here also needs the user's current password.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accounts;
    private final AccountSelfService selfService;
    private final InternalTokenFilter internalToken;

    public AccountController(AccountService accounts, AccountSelfService selfService, InternalTokenFilter internalToken) {
        this.accounts = accounts;
        this.selfService = selfService;
        this.internalToken = internalToken;
    }

    /**
     * Changes the password of the acting user. Needs {@code currentPassword} and a {@code newPassword} of at least 10
     * characters that differs from it. 204 on success; 401 {@code BAD_CREDENTIALS} if the current password is wrong; 429
     * {@code TOO_MANY_ATTEMPTS} while the client address is slowed down. Other sessions of the user stay logged in.
     */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        if (request.currentPassword() == null || request.newPassword() == null) {
            throw new BadRequestException("'currentPassword' and 'newPassword' are required");
        }
        AppUser user = actingUser(http);
        selfService.changePassword(user, request.currentPassword(), request.newPassword(), ip(http));
    }

    /**
     * Changes the email (the login name) of the acting user. Needs the user's {@code password} and the new
     * {@code email}. 200 with the user ({@link CurrentUserDto}); 400 for an invalid address; 401 {@code BAD_CREDENTIALS}
     * for a wrong password; 409 {@code EMAIL_TAKEN} if another account has it; 429 while the client address is slowed down.
     */
    @PutMapping("/email")
    public CurrentUserDto changeEmail(@RequestBody ChangeEmailRequest request, HttpServletRequest http) {
        if (request.password() == null || request.email() == null) {
            throw new BadRequestException("'password' and 'email' are required");
        }
        AppUser user = actingUser(http);
        AppUser updated = selfService.changeEmail(user, request.password(), request.email(), ip(http));
        return CurrentUserDto.from(updated, accounts.membershipFor(updated.getId()).orElse(null));
    }

    private AppUser actingUser(HttpServletRequest http) {
        int id = ActingUser.require(http, internalToken);
        return accounts.findById(id).orElseThrow(() -> new UnauthorizedException("Unknown user"));
    }

    private String ip(HttpServletRequest http) {
        return AuditFilter.ipOf(http, internalToken.hasValidToken(http));
    }
}
