package de.dahaem.api;

import de.dahaem.auth.AccountService;
import de.dahaem.auth.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The parts of the login flow that are plain endpoints. Login and logout themselves are handled
 * by Spring Security's filters (see {@link SecurityConfig}) before a request reaches a controller.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** The CSRF token to send with the next POST/PUT/DELETE. Public, because the login needs one too. */
    @GetMapping("/csrf")
    public CsrfTokenDto csrf(CsrfToken token) {
        return new CsrfTokenDto(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    /** The logged-in user and their household. */
    @GetMapping("/me")
    public CurrentUserDto me(Authentication authentication) {
        AppUser user = accountService.findByEmail(authentication.getName())
                // The session outlived the account (deleted by hand in the database).
                .orElseThrow(() -> new UnauthorizedException("Login required"));
        return CurrentUserDto.from(user, accountService.membershipFor(user.getId()).orElse(null));
    }
}
