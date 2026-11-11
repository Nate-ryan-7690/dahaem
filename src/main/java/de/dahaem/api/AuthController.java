package de.dahaem.api;

import de.dahaem.auth.InvalidCredentialsException;
import de.dahaem.auth.LoginService;
import de.dahaem.auth.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checking a login for the PHP front end, which owns the user's session. The API itself has no login page, no session
 * login and no CSRF endpoint.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final InternalTokenFilter internalToken;

    public AuthController(LoginService loginService, InternalTokenFilter internalToken) {
        this.loginService = loginService;
        this.internalToken = internalToken;
    }

    /**
     * Checks an email and a password for the PHP front end, which owns the user's session. Answers 200 with the user and
     * their household and role ({@link CurrentUserDto}); starts NO session and sets NO cookie. A wrong password and an
     * unknown email give the same 401 (code BAD_CREDENTIALS). After repeated attempts from one client address the answer is
     * 429 (code TOO_MANY_ATTEMPTS) with a {@code Retry-After} header, without looking at the password. The client address
     * comes from the {@code X-Client-Ip} header the PHP sends; the attempt is written to the audit log.
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
}
