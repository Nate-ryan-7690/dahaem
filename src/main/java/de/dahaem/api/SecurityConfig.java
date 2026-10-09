package de.dahaem.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dahaem.auth.AccountService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;

import java.io.IOException;

/**
 * Who may call the API. Session-based form login, meant for the same-origin PWA:
 *
 * <ul>
 *   <li>{@code POST /api/auth/login} (form fields {@code email}, {@code password}) checks the password
 *       against the BCrypt hash in app_user and starts an HTTP session (JSESSIONID cookie).
 *       204 on success, 401 on a wrong email or password.</li>
 *   <li>{@code POST /api/auth/logout} ends the session. 204.</li>
 *   <li>Every other {@code /api/**} request needs a logged-in session, else 401 (JSON, never a redirect,
 *       so the frontend can react by showing its login screen).</li>
 *   <li>Everything outside {@code /api/**} is the static PWA shell and stays public, so the login
 *       screen itself can load.</li>
 * </ul>
 *
 * <p>When {@code INTERNAL_API_TOKEN} is set, {@link InternalTokenFilter} additionally requires it on
 * every {@code /api/**} request and lets such requests through without a session or CSRF token.
 *
 * <p>CSRF protection is on for every state-changing request, the login and logout included. The
 * token lives in the session; the frontend fetches it from {@code GET /api/auth/csrf} and sends it
 * back in the header that endpoint names. Logging in rotates both the session id and the token.
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Looks users up by email (the login name) through the account service; roles are not used yet. */
    @Bean
    public UserDetailsService userDetailsService(AccountService accountService) {
        return email -> accountService.findByEmail(email)
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
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
                        .requestMatchers("/api/auth/csrf").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        // The PWA draws its own login screen; naming a page here only stops Spring
                        // from generating one at /login. Unauthenticated requests get a 401, not a redirect.
                        .loginPage("/")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) ->
                                writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, "Wrong email or password")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, "Login required"))
                        // Reached for a missing/stale CSRF token: the frontend refetches it and retries.
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, HttpStatus.FORBIDDEN, "Forbidden (missing or expired CSRF token)")))
                // An API client that hits a 401 does not want to be sent back to that URL after
                // logging in, and remembering it would open a session for every anonymous request.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()));
        return http.build();
    }

    // Same JSON shape as ApiExceptionHandler's errors, so the frontend reads every error the same way.
    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                                   String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
