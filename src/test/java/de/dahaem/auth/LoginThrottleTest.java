package de.dahaem.auth;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Failed logins slow the caller down (a growing delay), they never lock anybody out for good. */
class LoginThrottleTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong clock = new AtomicLong();
    private final LoginThrottle throttle = new LoginThrottle(clock::get);

    private void seconds(long s) {
        clock.addAndGet(s * SECOND);
    }

    @Test
    void fourFailuresFromOneAddressStillMayTryTheFifthBlocksForThirtySeconds() {
        for (int i = 0; i < 4; i++) {
            throttle.failure("1.1.1.1", "a@x.de");
        }
        assertTrue(throttle.retryAfterSeconds("1.1.1.1", "a@x.de").isEmpty());

        throttle.failure("1.1.1.1", "a@x.de");

        assertEquals(OptionalLong.of(30), throttle.retryAfterSeconds("1.1.1.1", "a@x.de"));
        seconds(10);
        assertEquals(OptionalLong.of(20), throttle.retryAfterSeconds("1.1.1.1", "a@x.de"), "it counts down");
        seconds(21);
        assertTrue(throttle.retryAfterSeconds("1.1.1.1", "a@x.de").isEmpty());
    }

    @Test
    void theDelayDoublesWithEachFurtherFailureUpToFifteenMinutes() {
        for (int i = 0; i < 5; i++) {
            throttle.failure("1.1.1.1", "x" + i + "@x.de");
        }
        seconds(31);
        throttle.failure("1.1.1.1", "y@x.de");   // the sixth
        assertEquals(OptionalLong.of(60), throttle.retryAfterSeconds("1.1.1.1", "z@x.de"));

        for (int i = 0; i < 30; i++) {
            seconds(1000);
            throttle.failure("1.1.1.1", "w" + i + "@x.de");
        }
        assertEquals(OptionalLong.of(900), throttle.retryAfterSeconds("1.1.1.1", "q@x.de"), "never more than 15 minutes");
    }

    @Test
    void otherAddressesAreNotAffected() {
        for (int i = 0; i < 5; i++) {
            throttle.failure("1.1.1.1", "a" + i + "@x.de");
        }
        assertTrue(throttle.retryAfterSeconds("2.2.2.2", "b@x.de").isEmpty());
    }

    @Test
    void manyFailuresForOneEmailFromManyAddressesSlowThatEmailForAtMostFiveMinutes() {
        for (int i = 0; i < 19; i++) {
            throttle.failure("10.0.0." + i, "real@x.de");
        }
        assertTrue(throttle.retryAfterSeconds("10.0.1.1", "real@x.de").isEmpty());

        throttle.failure("10.0.0.99", "real@x.de");   // the 20th

        assertEquals(OptionalLong.of(30), throttle.retryAfterSeconds("10.0.1.1", "real@x.de"));
        for (int i = 0; i < 30; i++) {
            seconds(1000);
            throttle.failure("10.0.2." + i, "real@x.de");
        }
        assertEquals(OptionalLong.of(300), throttle.retryAfterSeconds("10.0.3.3", "real@x.de"));
    }

    @Test
    void aCorrectLoginClearsTheEmailButNotTheAddress() {
        for (int i = 0; i < 5; i++) {
            throttle.failure("1.1.1.1", "mine@x.de");
        }
        throttle.success("mine@x.de");   // logging in to one's own account must not reset the guessing against others

        assertEquals(OptionalLong.of(30), throttle.retryAfterSeconds("1.1.1.1", "other@x.de"));
        assertTrue(throttle.retryAfterSeconds("9.9.9.9", "mine@x.de").isEmpty());
    }

    @Test
    void failuresAreForgottenAfterAnHour() {
        for (int i = 0; i < 4; i++) {
            throttle.failure("1.1.1.1", "a@x.de");
        }
        seconds(3601);

        throttle.failure("1.1.1.1", "a@x.de");

        assertTrue(throttle.retryAfterSeconds("1.1.1.1", "a@x.de").isEmpty(), "counting starts again");
    }
}
