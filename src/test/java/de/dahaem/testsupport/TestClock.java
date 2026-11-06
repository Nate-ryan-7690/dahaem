package de.dahaem.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** A clock a test can set to a day and move forward, so "today" is under the test's control. */
public final class TestClock extends Clock {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private Instant now;

    public TestClock(LocalDate today) {
        setToday(today);
    }

    /** Midday on this day in the household's zone. */
    public void setToday(LocalDate today) {
        this.now = today.atTime(12, 0).atZone(ZONE).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
