package de.dahaem.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/**
 * A delay that grows with login attempts from one client address, not a lockout. The attempt is counted BEFORE the
 * password is looked at (so many attempts sent at the same moment cannot all slip through while the first is still being
 * checked): the first {@value #THRESHOLD} attempts are allowed, the next ones are refused for 30 seconds, then 60, 120 ...
 * up to 15 minutes. A correct password gives its own attempt back. Attempts are forgotten after an hour without one.
 *
 * <p>There is deliberately no limit per email: such a limit lets anybody who knows an email address keep the real user
 * out by guessing wrong once in a while. Guessing spread over many addresses is for the edge (Cloudflare) to stop.
 * In memory: a restart clears it. Thread-safe (one lock; the work under it is tiny, the password check is outside).
 */
public class LoginThrottle {

    public static final int THRESHOLD = 5;
    private static final long SECOND = 1_000_000_000L;
    private static final long BASE_COOLDOWN = 30 * SECOND;
    private static final long MAX_COOLDOWN = 15 * 60 * SECOND;
    private static final long FORGET_AFTER = 3600 * SECOND;
    private static final int MAX_ENTRIES = 10_000;

    private static final class Entry {
        int attempts;
        long lastAttempt;
        long blockedUntil;
    }

    private final Map<String, Entry> byIp = new HashMap<>();
    private final LongSupplier nanoClock;

    public LoginThrottle(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /**
     * Asks to make an attempt. Empty: go ahead (the attempt is counted now). Otherwise the seconds to wait; nothing is
     * counted for a refused attempt.
     */
    public synchronized OptionalLong begin(String ip) {
        long now = nanoClock.getAsLong();
        String key = ip == null ? "" : ip;
        Entry existing = byIp.get(key);
        if (existing != null && existing.blockedUntil > now) {
            return OptionalLong.of((existing.blockedUntil - now + SECOND - 1) / SECOND);
        }
        if (byIp.size() > MAX_ENTRIES) {
            byIp.values().removeIf(e -> now - e.lastAttempt > FORGET_AFTER);
            if (byIp.size() > MAX_ENTRIES) {
                byIp.clear();   // a flood of addresses: forget everything rather than grow without limit
            }
        }
        Entry entry = existing == null || now - existing.lastAttempt > FORGET_AFTER ? new Entry() : existing;
        entry.attempts++;
        entry.lastAttempt = now;
        if (entry.attempts >= THRESHOLD) {
            int over = Math.min(entry.attempts - THRESHOLD, 20);
            entry.blockedUntil = now + Math.min(BASE_COOLDOWN << over, MAX_COOLDOWN);
        }
        byIp.put(key, entry);
        return OptionalLong.empty();
    }

    /** The password was right: the attempt does not count (and does not leave a block behind that only it caused). */
    public synchronized void succeeded(String ip) {
        Entry entry = byIp.get(ip == null ? "" : ip);
        if (entry == null) {
            return;
        }
        entry.attempts = Math.max(0, entry.attempts - 1);
        if (entry.attempts < THRESHOLD) {
            entry.blockedUntil = 0;
        }
    }
}
