package com.lms.config;

import java.time.LocalDate;

/**
 * The business date used by all day-based logic (due dates, accrual, DPD). Business days are
 * simulated so wall-clock timers cannot drive business-day logic (docs/architecture.md): in dev the
 * date is read from Redis and can be advanced for testing, falling back to today in {@code
 * Asia/Kolkata}. Business code injects this, never {@code LocalDate.now()} (CLAUDE.md).
 */
public interface BusinessCalendar {

    /** The current business date in {@code Asia/Kolkata}. */
    LocalDate today();
}
