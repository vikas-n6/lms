package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure derivation of a loan's overdue position (phase prompt task 1). Given the schedule lines, the
 * business date and the product grace days, it computes days-past-due (from the oldest unpaid
 * past-due instalment), the DPD bucket, the overdue component totals and the penal base. No clock,
 * no I/O — the caller supplies the business date (docs/architecture.md "BusinessCalendar").
 */
public final class Delinquency {

    private Delinquency() {}

    public static DelinquencySnapshot assess(
            List<LoanScheduleLine> lines,
            Currency currency,
            LocalDate businessDate,
            int graceDays) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(businessDate, "businessDate");
        if (graceDays < 0) {
            throw new IllegalArgumentException("graceDays must be non-negative");
        }

        Money overduePrincipal = Money.zero(currency);
        Money overdueInterest = Money.zero(currency);
        Money overduePenal = Money.zero(currency);
        Money penalBase = Money.zero(currency);
        int overdueCount = 0;
        Optional<LocalDate> oldestUnpaidDue = Optional.empty();
        Optional<Integer> oldestPastGrace = Optional.empty();

        for (LoanScheduleLine line : lines) {
            if (line.isFullyPaid() || line.dueDate().isAfter(businessDate)) {
                continue; // not yet due, or settled
            }
            // Past due (dueDate <= businessDate) and still owing.
            overdueCount++;
            overduePrincipal = overduePrincipal.add(line.principalOutstanding());
            overdueInterest = overdueInterest.add(line.interestOutstanding());
            overduePenal = overduePenal.add(line.penalOutstanding());
            if (oldestUnpaidDue.isEmpty()) {
                oldestUnpaidDue = Optional.of(line.dueDate());
            }
            // Penal base: only instalments past the grace window (docs/domain-rules.md).
            if (businessDate.isAfter(line.dueDate().plusDays(graceDays))) {
                penalBase =
                        penalBase.add(line.principalOutstanding()).add(line.interestOutstanding());
                if (oldestPastGrace.isEmpty()) {
                    oldestPastGrace = Optional.of(line.instalmentNumber());
                }
            }
        }

        int dpd = DpdBucket.daysPastDue(oldestUnpaidDue, businessDate);
        return new DelinquencySnapshot(
                dpd,
                DpdBucket.of(dpd),
                overdueCount,
                overduePrincipal,
                overdueInterest,
                overduePenal,
                penalBase,
                oldestPastGrace,
                oldestUnpaidDue);
    }
}
