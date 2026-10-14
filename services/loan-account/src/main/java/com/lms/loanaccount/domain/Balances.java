package com.lms.loanaccount.domain;

import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.Foreclosure;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure derivation of loan balances and the foreclosure quote from the schedule lines (docs/domain-
 * rules.md). Balances are always computed from postings/lines, never stored as a mutable counter
 * (phase prompt "Do not"). The foreclosure quote reuses {@link Foreclosure} (ADR 0005).
 */
public final class Balances {

    private Balances() {}

    public static LoanBalances of(
            List<LoanScheduleLine> lines, Currency currency, LocalDate businessDate) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(businessDate, "businessDate");

        Money principal = Money.zero(currency);
        Money interest = Money.zero(currency);
        Money penal = Money.zero(currency);
        Money dueNow = Money.zero(currency);
        Optional<LocalDate> nextDue = Optional.empty();

        for (LoanScheduleLine line : lines) {
            principal = principal.add(line.principalOutstanding());
            interest = interest.add(line.interestOutstanding());
            penal = penal.add(line.penalOutstanding());
            if (!line.dueDate().isAfter(businessDate)) {
                dueNow =
                        dueNow.add(line.penalOutstanding())
                                .add(line.interestOutstanding())
                                .add(line.principalOutstanding());
            }
            if (!line.isFullyPaid()
                    && (nextDue.isEmpty() || line.dueDate().isBefore(nextDue.get()))) {
                nextDue = Optional.of(line.dueDate());
            }
        }
        return new LoanBalances(principal, interest, penal, dueNow, nextDue);
    }

    /**
     * The foreclosure quote as of {@code quoteDate}: outstanding principal + interest accrued since
     * the last paid due date (actual/365) + unpaid penal + the foreclosure charge (ADR 0005).
     */
    public static Money foreclosureQuote(
            List<LoanScheduleLine> lines,
            Currency currency,
            Rate annualRate,
            Rate foreclosureChargeRate,
            LocalDate disbursementDate,
            LocalDate quoteDate) {
        Money outstandingPrincipal = Money.zero(currency);
        Money unpaidPenal = Money.zero(currency);
        LocalDate lastPaidDue = disbursementDate;
        for (LoanScheduleLine line : lines) {
            outstandingPrincipal = outstandingPrincipal.add(line.principalOutstanding());
            unpaidPenal = unpaidPenal.add(line.penalOutstanding());
            if (line.isFullyPaid() && line.dueDate().isAfter(lastPaidDue)) {
                lastPaidDue = line.dueDate();
            }
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(lastPaidDue, quoteDate));
        return Foreclosure.quote(
                outstandingPrincipal, annualRate, days, unpaidPenal, foreclosureChargeRate);
    }
}
