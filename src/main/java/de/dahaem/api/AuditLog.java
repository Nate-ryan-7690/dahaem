package de.dahaem.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * The audit trail: one line per login, failed login, write and refused request, in the logger "audit", with the client
 * IP the PHP front end passes on. Never a request body, a query string, a password or a token.
 */
public final class AuditLog {

    private static final Logger LOG = LoggerFactory.getLogger("audit");
    /** Characters of an IPv4 / IPv6 literal, with a dot or a colon in it; no host names, no spaces, no line breaks. */
    private static final Pattern IP_LITERAL = Pattern.compile("^(?=.*[.:])[0-9a-fA-F:.]{2,45}$");
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,100}$");

    /** The header the PHP front end sends with the real client address (it sees Cloudflare's, not the client's). */
    public static final String CLIENT_IP_HEADER = "X-Client-Ip";
    /** The header the PHP front end sends with the id of the logged-in user (believed only together with the token). */
    public static final String ACTING_USER_HEADER = "X-Acting-User";
    private static final Pattern USER_ID = Pattern.compile("^[0-9]{1,9}$");

    private AuditLog() {
    }

    /**
     * The client address to log. The header is believed only when the request carried the internal token (so only
     * the PHP front end can set it) and holds an IP literal; otherwise the address the connection came from.
     */
    static String clientIp(String headerValue, String remoteAddr, boolean tokenValid) {
        if (tokenValid && headerValue != null) {
            String candidate = headerValue.trim();
            if (IP_LITERAL.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return remoteAddr == null ? "-" : remoteAddr;
    }

    /** The acting user id for the log: the header, but only with the internal token and only a plain number; else "-". */
    static String actingUser(String headerValue, boolean tokenValid) {
        if (tokenValid && headerValue != null && USER_ID.matcher(headerValue.trim()).matches()) {
            return headerValue.trim();
        }
        return "-";
    }

    /** Takes anything that could break a log line out of a value and shortens it. */
    static String clean(String value, int max) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "?");
        return cleaned.length() > max ? cleaned.substring(0, max) + "..." : cleaned;
    }

    /** A request worth keeping: a write, or any request that was refused (401 / 403). */
    public static void request(String method, String path, int status, String caller, String ip, String user) {
        LOG.info("request method={} path={} status={} caller={} user={} ip={}", method, clean(path, 200), status, caller,
                user, ip);
    }

    /**
     * A login attempt. The typed email is logged only if it looks like an email address (somebody may have typed their
     * password into the wrong field), shortened.
     */
    public static void login(boolean success, String typedEmail, String ip) {
        String who = typedEmail != null && EMAIL_SHAPE.matcher(typedEmail.trim()).matches()
                ? clean(typedEmail.trim().toLowerCase(java.util.Locale.ROOT), 120) : "(not an email address)";
        if (success) {
            LOG.info("login ok user={} ip={}", who, ip);
        } else {
            LOG.warn("login FAILED user={} ip={}", who, ip);
        }
    }
}
