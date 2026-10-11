package com.lms.testkit.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * A {@link Clock} tests can move at will, for driving time-dependent business logic
 * deterministically (accruals, DPD, expiries). Business code only ever sees a {@code Clock}; tests
 * inject this.
 */
public final class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = Objects.requireNonNull(instant, "instant");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public static MutableClock at(Instant instant, ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    public static MutableClock utc(Instant instant) {
        return new MutableClock(instant, ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public void setInstant(Instant newInstant) {
        this.instant = Objects.requireNonNull(newInstant, "newInstant");
    }

    public void advance(Duration amount) {
        this.instant = this.instant.plus(amount);
    }
}
