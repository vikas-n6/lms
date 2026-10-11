package com.lms.schedule;

import java.time.LocalDate;

/**
 * Instalment due-date arithmetic (docs/domain-rules.md "Schedule engine" rule 4): the due date is
 * the anchor date plus {@code k} whole months, clamped to the last day of a shorter month. This is
 * exactly {@link LocalDate#plusMonths(long)} semantics (31 Jan + 1 month = 28/29 Feb; + 2 months =
 * 31 Mar, measured from the original 31st), so the clamp follows from the JDK and is proven by
 * test.
 */
final class DueDates {

    private DueDates() {}

    static LocalDate addMonthsClamped(LocalDate anchor, int months) {
        return anchor.plusMonths(months);
    }
}
