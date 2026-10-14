package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BalancesTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final UUID LOAN = UUID.randomUUID();
    private static final LocalDate DISBURSED = LocalDate.of(2026, 1, 15);

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    private LoanScheduleLine line(
            int n, LocalDate due, long principal, long interest, long paidP, long paidI) {
        return new LoanScheduleLine(
                LOAN,
                n,
                due,
                inr(principal),
                inr(interest),
                inr(paidP),
                inr(paidI),
                inr(0),
                inr(0),
                false);
    }

    @Test
    void derivesOutstandingAndDueNowAndNextDue() {
        List<LoanScheduleLine> lines =
                List.of(
                        line(1, LocalDate.of(2026, 2, 15), 10_000, 2_000, 10_000, 2_000), // paid
                        line(2, LocalDate.of(2026, 3, 15), 10_200, 1_800, 0, 0), // due
                        line(3, LocalDate.of(2026, 4, 15), 10_400, 1_600, 0, 0)); // future

        LoanBalances balances = Balances.of(lines, INR, LocalDate.of(2026, 3, 20));

        assertThat(balances.outstandingPrincipal()).isEqualTo(inr(20_600));
        assertThat(balances.interestOutstanding()).isEqualTo(inr(3_400));
        assertThat(balances.penalOutstanding()).isEqualTo(inr(0));
        // Due now = instalment 2 only (its due date <= business date; 3 is in the future).
        assertThat(balances.totalDueNow()).isEqualTo(inr(12_000));
        assertThat(balances.nextDueDate()).contains(LocalDate.of(2026, 3, 15));
    }

    @Test
    void foreclosureQuoteAddsAccruedInterestSinceLastPaidDue() {
        List<LoanScheduleLine> lines =
                List.of(
                        line(1, LocalDate.of(2026, 2, 15), 10_000, 2_000, 10_000, 2_000), // paid
                        line(2, LocalDate.of(2026, 3, 15), 40_000, 1_800, 0, 0)); // outstanding

        // Outstanding principal 40,000 paise (= 400.00); 20 days since the last paid due
        // (2026-02-15) to 2026-03-07 at 24%/365; penal 0; charge 0 (default).
        Money quote =
                Balances.foreclosureQuote(
                        lines,
                        INR,
                        Rate.percent(24),
                        Rate.zero(),
                        DISBURSED,
                        LocalDate.of(2026, 3, 7));

        long accrued = Math.round(40_000L * 0.24 * 20 / 365.0); // 526 paise
        assertThat(quote).isEqualTo(inr(40_000L + accrued));
    }
}
