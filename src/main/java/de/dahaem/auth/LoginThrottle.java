package de.dahaem.auth;

import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A delay that grows with failed logins, not a lockout. After {@value #IP_THRESHOLD} failures from one client address
 * (or {@value #EMAIL_THRESHOLD} for one email) further attempts are refused for 30 seconds, then 60, 120 ... up to 15
 * minutes for an address and 5 minutes for an email. The address limit is strict; the email limit is generous and short,
 * so somebody guessing wrong passwords for the real user's email from elsewhere can slow the real user down by at most
 * a few minutes and never lock them out. Failures are forgotten after an hour without one. A correct login clears the
 * email's count but not the address's (otherwise a login to one's own account would reset the guessing against another).
 * In memory: a restart clears it. Thread-safe.
 */
public class LoginThrottle {

    public static final int IP_THRESHOLD = 5;
    public static final int EMAIL_THRESHOLD = 20;
    private static final long SECOND = 1_000_000_000L;
    private static final long BASE_COOLDOWN = 30 * SECOND;
    private static final long IP_MAX_COOLDOWN = 15 * 60 * SECOND;
    private static final long EMAIL_MAX_COOLDOWN = 5 * 60 * SECOND;
    private static final long FORGET_AFTER = 3600 * SECOND;
    private static final int MAX_ENTRIES = 10_000;

    private static final class Entry {
        int failures;
        long lastFailure;
        long blockedUntil;
    }

    private final Map<String, Entry> byIp = new ConcurrentHashMap<>();
    private final Map<String, Entry> byEmail = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;

    public LoginThrottle(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /** Seconds to wait before this address / email may try again, or empty if it may try now. */
    public OptionalLong retryAfterSeconds(String ip, String email) {
        long now = nanoClock.getAsLong();
        long remaining = Math.max(remaining(byIp.get(key(ip)), now), remaining(byEmail.get(key(email)), now));
        return remaining > 0 ? OptionalLong.of((remaining + SECOND - 1) / SECOND) : OptionalLong.empty();
    }

    public void failure(String ip, String email) {
        long now = nanoClock.getAsLong();
        record(byIp, key(ip), IP_THRESHOLD, IP_MAX_COOLDOWN, now);
        record(byEmail, key(email), EMAIL_THRESHOLD, EMAIL_MAX_COOLDOWN, now);
    }

    /** A correct login: forget the failures for this email (not for the address). */
    public void success(String email) {
        byEmail.remove(key(email));
    }

    private static String key(String value) {
        return value == null ? "" : value;
    }

    private static long remaining(Entry entry, long now) {
        return entry == null ? 0 : entry.blockedUntil - now;
    }

    private void record(Map<String, Entry> map, String key, int threshold, long maxCooldown, long now) {
        if (map.size() > MAX_ENTRIES) {
            map.values().removeIf(e -> now - e.lastFailure > FORGET_AFTER);
            if (map.size() > MAX_ENTRIES) {
                map.clear();   // a flood of addresses: forget everything rather than grow without limit
            }
        }
        map.compute(key, (k, existing) -> {
            Entry entry = existing == null || now - existing.lastFailure > FORGET_AFTER ? new Entry() : existing;
            entry.failures++;
            entry.lastFailure = now;
            if (entry.failures >= threshold) {
                int over = Math.min(entry.failures - threshold, 20);
                entry.blockedUntil = now + Math.min(BASE_COOLDOWN << over, maxCooldown);
            }
            return entry;
        });
    }
}
