package com.lms.notification.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * The quiet-hours window (phase prompt task 5): messages created inside it are held and released at
 * the window's end "the next morning". Evaluated against the business-calendar time zone (default
 * {@code Asia/Kolkata}) from the injected clock, so it is deterministic and never driven by a
 * wall-clock timer. The default window 21:00→08:00 wraps midnight.
 *
 * @param startHour inclusive start hour (0–23), e.g. 21
 * @param endHour exclusive end hour (0–23), e.g. 8
 * @param zone the business time zone
 */
public record QuietHours(int startHour, int endHour, ZoneId zone) {

    public QuietHours {
        Objects.requireNonNull(zone, "zone");
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException("hours must be 0..23");
        }
    }

    private boolean wrapsMidnight() {
        return startHour > endHour;
    }

    /** True when {@code at} falls inside the quiet window. */
    public boolean isQuiet(Instant at) {
        int hour = ZonedDateTime.ofInstant(at, zone).getHour();
        if (wrapsMidnight()) {
            return hour >= startHour || hour < endHour;
        }
        return hour >= startHour && hour < endHour;
    }

    /**
     * When a message created at {@code at} (inside the window) should be released: the next
     * occurrence of {@code endHour} at or after {@code at}. For a message not in the window,
     * returns {@code at} unchanged.
     */
    public Instant releaseAt(Instant at) {
        if (!isQuiet(at)) {
            return at;
        }
        ZonedDateTime zoned = ZonedDateTime.ofInstant(at, zone);
        // If we are after midnight but still before endHour, release later today; otherwise
        // tomorrow.
        if (wrapsMidnight() && zoned.getHour() < endHour) {
            return zoned.with(LocalTime.of(endHour, 0)).toInstant();
        }
        return zoned.with(LocalTime.of(endHour, 0)).plusDays(1).toInstant();
    }
}
