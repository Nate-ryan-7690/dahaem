package de.dahaem.barcode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The outside barcode lookup has a ceiling per minute and sends nothing over it. */
class RateLimitedSourceTest {

    private static class Counting implements ProductDataSource {
        int calls;

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Optional<ProductSuggestion> find(String code) {
            calls++;
            return Optional.empty();
        }
    }

    @Test
    void callsOverTheCeilingAreRefusedAndNotSent() throws IOException {
        Counting inner = new Counting();
        AtomicLong clock = new AtomicLong();
        RateLimitedSource source = new RateLimitedSource(inner, 3, clock::get);

        for (int i = 0; i < 3; i++) {
            source.find("73513537");
        }
        assertThrows(IOException.class, () -> source.find("73513537"));
        assertEquals(3, inner.calls, "the refused call must not reach the outside source");
    }

    @Test
    void theCeilingIsPerSlidingMinute() throws IOException {
        Counting inner = new Counting();
        AtomicLong clock = new AtomicLong();
        RateLimitedSource source = new RateLimitedSource(inner, 2, clock::get);

        source.find("73513537");                       // t = 0
        clock.set(30_000_000_000L);
        source.find("73513537");                       // t = 30 s
        assertThrows(IOException.class, () -> source.find("73513537"));
        clock.set(61_000_000_000L);                    // the first call is more than a minute old
        source.find("73513537");
        assertThrows(IOException.class, () -> source.find("73513537"), "the second is still inside the minute");
        assertEquals(3, inner.calls);
    }

    @Test
    void enabledFollowsTheWrappedSource() {
        assertTrue(new RateLimitedSource(new Counting(), 5, () -> 0).enabled());
        assertEquals(false, new RateLimitedSource(ProductDataSource.disabled(), 5, () -> 0).enabled());
    }
}
