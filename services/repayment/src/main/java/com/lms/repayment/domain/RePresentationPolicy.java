package com.lms.repayment.domain;

import java.time.LocalDate;

/**
 * When a failed auto-debit is re-presented (phase prompt task 3: "re-presented once two business
 * days later"). Pure; the gap in business days comes from configuration, never hard-coded at the
 * call site (CLAUDE.md). A debit is re-presented at most once — {@link #alreadyRepresented} gates
 * the second failure.
 */
public final class RePresentationPolicy {

    private RePresentationPolicy() {}

    /** The business date to re-present on, {@code businessDaysLater} after the failed date. */
    public static LocalDate rePresentOn(
            LocalDate failedBusinessDate,
            int businessDaysLater,
            java.util.Set<LocalDate> holidays) {
        LocalDate date = failedBusinessDate;
        int added = 0;
        while (added < businessDaysLater) {
            date = date.plusDays(1);
            if (!isWeekend(date) && !holidays.contains(date)) {
                added++;
            }
        }
        return date;
    }

    /** Whether a debit that already carries a re-presentation attempt may be re-presented again. */
    public static boolean alreadyRepresented(int presentationCount) {
        return presentationCount >= 2;
    }

    private static boolean isWeekend(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> false;
        };
    }
}
