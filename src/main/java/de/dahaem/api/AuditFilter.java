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
 * Writes the audit line (see {@link AuditLog}) for every request, on any path, that is a write or was refused with 401 /
 * 403. It runs before Spring Security, so refused requests (a wrong token) are seen too. The verify endpoint logs its own
 * line with the typed email. The path is not used to decide what to log (the raw path can be percent-encoded).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditFilter extends OncePerRequestFilter {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final InternalTokenFilter token;

    public AuditFilter(InternalTokenFilter token) {
        this.token = token;
    }

    /** The verify endpoint writes its own audit line (with the typed email); decided on the decoded path. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/auth/verify".equals(request.getServletPath());
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
