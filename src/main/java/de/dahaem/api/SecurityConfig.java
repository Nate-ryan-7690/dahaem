package de.dahaem.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dahaem.auth.AccountService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

import java.io.IOException;

/**
 * Who may call the API: only the PHP front end, which proves it with the shared secret in the {@code X-Internal-Token}
 * header on EVERY request (see {@link InternalTokenFilter}). The PHP front end owns the user login and the user session
 * ({@code POST /api/auth/verify} checks a password); the API has no login page, no session login and no static pages.
 *
 * <ul>
 *   <li>{@code /api/**} needs the token (or, in tests, a mock user), else 401 as JSON, never a redirect.</li>
 *   <li>Everything else is denied (the API serves no pages). {@code /error} stays open only so that error responses can
 *       be rendered.</li>
 * </ul>
 *
 * <p>CSRF protection stays on for state-changing requests that do not carry the token; with the token required on every
 * path none can reach a controller in production. The PHP front end has its own CSRF tokens on its forms.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The token filter belongs in the Spring Security chain only, not also in the servlet container's chain. */
    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilterRegistration(InternalTokenFilter filter) {
        FilterRegistrationBean<InternalTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** BCrypt (salted per hash). The cost is a setting so tests can use a cheap one; 12 in production. */
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${dahaem.auth.bcrypt-strength:" + AccountService.DEFAULT_BCRYPT_STRENGTH + "}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                   InternalTokenFilter internalToken) throws Exception {
        http
                // The PHP front end authenticates with the shared internal token instead of a browser
                // session, so its calls carry no CSRF token. See InternalTokenFilter.
                .csrf(csrf -> csrf.ignoringRequestMatchers(internalToken::hasValidToken))
                .addFilterBefore(internalToken, CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "Authentication required"))
                        .accessDeniedHandler((request, response, exception) -> {
                            if (exception instanceof CsrfException) {
                                writeError(response, objectMapper, HttpStatus.FORBIDDEN, "CSRF_INVALID", "Forbidden (missing or expired CSRF token)");
                            } else {
                                writeError(response, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden");
                            }
                        }))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()));
        return http.build();
    }

    // Same JSON shape as ApiExceptionHandler's errors, so the frontend reads every error the same way.
    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                                   String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse(status.value(), status.getReasonPhrase(), message, code));
    }
}
