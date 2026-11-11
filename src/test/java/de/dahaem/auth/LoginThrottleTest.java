package de.dahaem.auth;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Attempts from one address are slowed down (a growing delay), counted before the password is looked at. */
class LoginThrottleTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong clock = new AtomicLong();
    private final LoginThrottle throttle = new LoginThrottle(clock::get);

    private void seconds(long s) {
        clock.addAndGet(s * SECOND);
    }

    @Test
    void theFirstFiveAttemptsAreAllowedAndTheSixthIsRefusedForThirtySeconds() {
        for (int i = 0; i < 5; i++) {
            assertTrue(throttle.begin("1.1.1.1").isEmpty(), "attempt " + (i + 1));
        }

        assertEquals(OptionalLong.of(30), throttle.begin("1.1.1.1"));
        seconds(10);
        assertEquals(OptionalLong.of(20), throttle.begin("1.1.1.1"), "it counts down, and a refused attempt does not count");
        seconds(21);
        assertTrue(throttle.begin("1.1.1.1").isEmpty());
    }

    @Test
    void manyAttemptsAtTheSameMomentCannotAllSlipThrough() {
        int allowed = 0;
        for (int i = 0; i < 40; i++) {   // none of them has finished its password check yet: the clock does not move
            if (throttle.begin("1.1.1.1").isEmpty()) {
                allowed++;
            }
        }
        assertEquals(5, allowed);
    }

    @Test
    void theDelayDoublesWithEachFurtherAttemptUpToFifteenMinutes() {
        for (int i = 0; i < 5; i++) {
            throttle.begin("1.1.1.1");
        }
        seconds(31);
        throttle.begin("1.1.1.1");   // the sixth: allowed after the first delay, and the next delay is 60 s
        assertEquals(OptionalLong.of(60), throttle.begin("1.1.1.1"));

        for (int i = 0; i < 30; i++) {
            seconds(1000);
            throttle.begin("1.1.1.1");
        }
        assertEquals(OptionalLong.of(900), throttle.begin("1.1.1.1"), "never more than 15 minutes");
    }

    @Test
    void otherAddressesAreNotAffected() {
        for (int i = 0; i < 6; i++) {
            throttle.begin("1.1.1.1");
        }
        assertTrue(throttle.begin("2.2.2.2").isEmpty());
    }

    @Test
    void aCorrectPasswordGivesItsOwnAttemptBackAndRemovesTheBlockOnlyItCaused() {
        for (int i = 0; i < 4; i++) {
            throttle.begin("1.1.1.1");   // four wrong guesses
        }
        assertTrue(throttle.begin("1.1.1.1").isEmpty());   // the fifth attempt triggers the block ...
        throttle.succeeded("1.1.1.1");                     // ... and was a correct login, so it is undone

        assertTrue(throttle.begin("1.1.1.1").isEmpty(), "back at four attempts: one more is allowed");
        assertEquals(OptionalLong.of(30), throttle.begin("1.1.1.1"), "that was the fifth guess: now it blocks");
    }

    @Test
    void loggingInToOneOwnAccountDoesNotResetTheGuessingAgainstAnother() {
        for (int i = 0; i < 4; i++) {
            throttle.begin("1.1.1.1");   // four wrong guesses
        }
        for (int i = 0; i < 10; i++) {
            if (throttle.begin("1.1.1.1").isEmpty()) {
                throttle.succeeded("1.1.1.1");   // own successful logins in between
            }
        }
        // the four guesses are still counted: the next guess is the fifth and the one after it is refused
        assertTrue(throttle.begin("1.1.1.1").isEmpty());
        assertTrue(throttle.begin("1.1.1.1").isPresent());
    }

    @Test
    void attemptsAreForgottenAfterAnHour() {
        for (int i = 0; i < 4; i++) {
            throttle.begin("1.1.1.1");
        }
        seconds(3601);

        for (int i = 0; i < 5; i++) {
            assertTrue(throttle.begin("1.1.1.1").isEmpty(), "counting starts again: attempt " + (i + 1));
        }
        assertTrue(throttle.begin("1.1.1.1").isPresent());
    }

    @Test
    void anUnknownAddressIsOneSharedKeyNotAnError() {
        for (int i = 0; i < 5; i++) {
            throttle.begin(null);
        }
        assertTrue(throttle.begin(null).isPresent());
    }
}
