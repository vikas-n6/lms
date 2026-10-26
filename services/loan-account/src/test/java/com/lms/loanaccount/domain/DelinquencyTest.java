package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure overdue derivation (phase prompt task 1 acceptance tests): DPD from the oldest unpaid
 * past-due instalment, the penal base counting only instalments past the grace window, and the
 * hand-computed penal-per-day figure the accrual job uses.
 */
class DelinquencyTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final UUID LOAN = UUID.randomUUID();
    private static final int GRACE = 3;

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
    void currentWhenNothingPastDue() {
        List<LoanScheduleLine> lines =
                List.of(
                        line(1, LocalDate.of(2026, 2, 15), 10_000, 2_000, 10_000, 2_000), // paid
                        line(2, LocalDate.of(2026, 3, 15), 10_200, 1_800, 0, 0)); // future

        DelinquencySnapshot s = Delinquency.assess(lines, INR, LocalDate.of(2026, 3, 10), GRACE);

        assertThat(s.isCurrent()).isTrue();
        assertThat(s.daysPastDue()).isZero();
        assertThat(s.bucket()).isEqualTo(DpdBucket.CURRENT);
        assertThat(s.overdueInstalmentCount()).isZero();
        assertThat(s.penalBase()).isEqualTo(inr(0));
    }

    @Test
    void dpdCountsFromOldestUnpaidDueDateAndBucketsCorrectly() {
        List<LoanScheduleLine> lines =
                List.of(
                        line(1, LocalDate.of(2026, 2, 15), 10_000, 2_000, 0, 0), // unpaid, oldest
                        line(2, LocalDate.of(2026, 3, 15), 10_200, 1_800, 0, 0)); // unpaid

        // 40 days after the oldest unpaid due date (2026-02-15 -> 2026-03-27).
        DelinquencySnapshot s = Delinquency.assess(lines, INR, LocalDate.of(2026, 3, 27), GRACE);

        assertThat(s.daysPastDue()).isEqualTo(40);
        assertThat(s.bucket()).isEqualTo(DpdBucket.DPD_31_60);
        assertThat(s.overdueInstalmentCount()).isEqualTo(2);
        assertThat(s.oldestUnpaidDueDate()).contains(LocalDate.of(2026, 2, 15));
        assertThat(s.overduePrincipal()).isEqualTo(inr(20_200));
        assertThat(s.overdueInterest()).isEqualTo(inr(3_800));
        assertThat(s.isNpa()).isFalse();
    }

    @Test
    void npaAtDpd91OrMore() {
        List<LoanScheduleLine> lines =
                List.of(line(1, LocalDate.of(2026, 1, 1), 10_000, 2_000, 0, 0));

        DelinquencySnapshot s = Delinquency.assess(lines, INR, LocalDate.of(2026, 4, 2), GRACE);

        assertThat(s.daysPastDue()).isEqualTo(91);
        assertThat(s.bucket()).isEqualTo(DpdBucket.NPA);
        assertThat(s.isNpa()).isTrue();
    }

    @Test
    void penalBaseExcludesInstalmentsStillInsideTheGraceWindow() {
        // Instalment due 2026-02-15, grace 3 => penal starts only after 2026-02-18.
        List<LoanScheduleLine> lines =
                List.of(line(1, LocalDate.of(2026, 2, 15), 10_000, 2_000, 0, 0));

        // On 2026-02-18 (= due + grace), still within grace: DPD > 0 but no penal base.
        DelinquencySnapshot inGrace =
                Delinquency.assess(lines, INR, LocalDate.of(2026, 2, 18), GRACE);
        assertThat(inGrace.daysPastDue()).isEqualTo(3);
        assertThat(inGrace.penalBase()).isEqualTo(inr(0));

        // On 2026-02-19 (> due + grace): penal base = overdue principal + interest.
        DelinquencySnapshot pastGrace =
                Delinquency.assess(lines, INR, LocalDate.of(2026, 2, 19), GRACE);
        assertThat(pastGrace.penalBase()).isEqualTo(inr(12_000));
        assertThat(pastGrace.oldestOverdueInstalment()).contains(1);
    }

    @Test
    void penalPerDayMatchesTheHandComputedFormula() {
        // Overdue principal 10,000.00 + interest 1,000.00 = base 11,000.00 (1,100,000 paise),
        // past the grace window. Penal APR 36% simple, actual/365:
        //   round(1,100,000 * 0.36 / 365) = round(1084.93) = 1085 paise.
        List<LoanScheduleLine> lines =
                List.of(line(1, LocalDate.of(2026, 2, 15), 1_000_000, 100_000, 0, 0));

        DelinquencySnapshot s = Delinquency.assess(lines, INR, LocalDate.of(2026, 3, 1), GRACE);
        assertThat(s.penalBase()).isEqualTo(inr(1_100_000));

        Money penalForDay = PenalAccrual.forDay(s.penalBase(), Rate.percent(36));
        assertThat(penalForDay).isEqualTo(inr(1_085));
    }
}
