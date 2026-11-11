package de.dahaem.barcode;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Puts a ceiling on the outside lookups so the barcode endpoint cannot be used to hammer a third-party source: at most
 * {@code maxPerMinute} calls in any 60 seconds, for the whole API. Over the ceiling the lookup fails like an unreachable
 * source (the answer says UNAVAILABLE) and nothing is sent.
 */
public class RateLimitedSource implements ProductDataSource {

    private static final long WINDOW_NANOS = 60_000_000_000L;

    private final ProductDataSource delegate;
    private final int maxPerMinute;
    private final LongSupplier nanoClock;
    private final Deque<Long> calls = new ArrayDeque<>();

    public RateLimitedSource(ProductDataSource delegate, int maxPerMinute, LongSupplier nanoClock) {
        this.delegate = delegate;
        this.maxPerMinute = Math.max(1, maxPerMinute);
        this.nanoClock = nanoClock;
    }

    @Override
    public boolean enabled() {
        return delegate.enabled();
    }

    @Override
    public Optional<ProductSuggestion> find(String code) throws IOException {
        synchronized (calls) {
            long now = nanoClock.getAsLong();
            while (!calls.isEmpty() && now - calls.peekFirst() >= WINDOW_NANOS) {
                calls.pollFirst();
            }
            if (calls.size() >= maxPerMinute) {
                throw new IOException("Too many outside lookups in the last minute (limit " + maxPerMinute + ")");
            }
            calls.addLast(now);
        }
        return delegate.find(code);
    }
}
