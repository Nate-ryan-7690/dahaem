package de.dahaem.api;

import de.dahaem.auth.AccountService;
import de.dahaem.auth.InvalidCredentialsException;
import de.dahaem.auth.LoginService;
import de.dahaem.auth.TooManyAttemptsException;
import de.dahaem.auth.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final LoginService loginService;
    private final InternalTokenFilter internalToken;

    public AuthController(AccountService accountService, LoginService loginService, InternalTokenFilter internalToken) {
        this.accountService = accountService;
        this.loginService = loginService;
        this.internalToken = internalToken;
    }

    /**
     * Checks an email and a password for the PHP front end, which owns the user's session. Answers 200 with the user and
     * their household and role ({@link CurrentUserDto}); starts NO session and sets NO cookie. A wrong password and an
     * unknown email give the same 401 (code BAD_CREDENTIALS). After repeated failures from one client address (or for one
     * email) the answer is 429 (code TOO_MANY_ATTEMPTS) with a {@code Retry-After} header, without looking at the password.
     * The client address comes from the {@code X-Client-Ip} header the PHP sends; the attempt is written to the audit log.
     */
    @PostMapping("/verify")
    public CurrentUserDto verify(@RequestBody VerifyRequest request, HttpServletRequest http) {
        if (request.email() == null || request.password() == null) {
            throw new BadRequestException("'email' and 'password' are required");
        }
        String ip = AuditFilter.ipOf(http, internalToken.hasValidToken(http));
        try {
            LoginService.Verified verified = loginService.verify(request.email(), request.password(), ip);
            AuditLog.login(true, verified.user().getEmail(), ip);
            return CurrentUserDto.from(verified.user(), verified.membership());
        } catch (InvalidCredentialsException | TooManyAttemptsException e) {
            AuditLog.login(false, request.email(), ip);
            throw e;
        }
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
