package de.dahaem.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Writes the audit line (see {@link AuditLog}) for every /api request that is a write or was refused with 401 / 403.
 * It runs before Spring Security, so refused requests (a wrong token, a missing login) are seen too. Logins are logged
 * by the login handlers and the verify endpoint with the typed email, so those paths are skipped here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditFilter extends OncePerRequestFilter {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final InternalTokenFilter token;

    public AuditFilter(InternalTokenFilter token) {
        this.token = token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.equals("/api/auth/login") || path.equals("/api/auth/verify");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            if (!READS.contains(request.getMethod()) || status == 401 || status == 403) {
                boolean internal = token.hasValidToken(request);
                AuditLog.request(request.getMethod(), request.getRequestURI(), status,
                        internal ? "internal" : "other", ipOf(request, internal),
                        AuditLog.actingUser(request.getHeader(AuditLog.ACTING_USER_HEADER), internal));
            }
        }
    }

    /** The client address for a request (see {@link AuditLog#clientIp}). */
    static String ipOf(HttpServletRequest request, boolean tokenValid) {
        return AuditLog.clientIp(request.getHeader(AuditLog.CLIENT_IP_HEADER), request.getRemoteAddr(), tokenValid);
    }
}
