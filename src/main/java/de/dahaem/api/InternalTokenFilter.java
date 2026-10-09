package de.dahaem.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared-secret gate for the internal API. When {@code INTERNAL_API_TOKEN} is set (property
 * {@code dahaem.internal-token}), every {@code /api/**} request must carry it in the
 * {@code X-Internal-Token} header: a missing or wrong token is answered with 401 and goes no
 * further. A request with the right token counts as authenticated (it is the PHP front end, which
 * does its own user login) and is exempt from CSRF, which only protects browser sessions.
 *
 * <p>When no token is configured the filter does nothing and the session login in
 * {@link SecurityConfig} applies as before (local development, tests).
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] expected;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(@Value("${dahaem.internal-token:}") String token, ObjectMapper objectMapper) {
        this.expected = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    /** True if the request carries the configured token (never true when none is configured). */
    public boolean hasValidToken(HttpServletRequest request) {
        String presented = request.getHeader(HEADER);
        // Constant-time comparison so the token cannot be guessed byte by byte from response timing.
        return expected != null && presented != null
                && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expected == null || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!hasValidToken(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                    HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    "Missing or invalid internal token"));
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "internal-api", null, AuthorityUtils.createAuthorityList("ROLE_INTERNAL")));
        chain.doFilter(request, response);
    }
}
