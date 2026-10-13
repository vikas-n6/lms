package com.lms.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Default {@link BusinessCalendar}: returns the {@link BusinessDateSource} override when present,
 * otherwise today in {@code Asia/Kolkata} derived from the injected {@link Clock} (UTC instants,
 * business dates in IST — docs/domain-rules.md "Time"). Pure and clock-driven, so tests move the
 * date by moving the clock or the source.
 */
public final class ConfigurableBusinessCalendar implements BusinessCalendar {

    /** Business dates (due dates, accrual days) are in IST (CLAUDE.md "Time"). */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final BusinessDateSource source;
    private final Clock clock;

    public ConfigurableBusinessCalendar(BusinessDateSource source, Clock clock) {
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LocalDate today() {
        return source.currentOverride()
                .orElseGet(() -> LocalDate.now(clock.withZone(BUSINESS_ZONE)));
    }
}
