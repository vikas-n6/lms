package com.lms.loanaccount.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
import com.lms.money.Money;
import com.lms.schedule.ReducingBalanceScheduleEngine;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The loan-account daily jobs (phase prompt task 1 acceptance tests): penal accrual after the grace
 * window and idempotent per date; DPD moving a loan to Delinquent and back to Active with {@code
 * loan.dpd-updated} published once on the return to zero; and the pre-due reminder published three
 * business days ahead, once. Pure in-memory fakes, a fixed clock and an explicit business date — no
 * Docker.
 */
class DailyJobsTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-13T06:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DISBURSED = LocalDate.of(2026, 10, 13);

    private static final class MutableCalendar implements BusinessCalendar {
        LocalDate date = DISBURSED;

        @Override
        public LocalDate today() {
            return date;
        }
    }

    private LoanAccountFakes.FakeLoanRepository loans;
    private LoanAccountFakes.FakeScheduleRepository schedules;
    private LoanAccountFakes.FakeStateHistoryRepository history;
    private LoanAccountFakes.FakeAccrualRepository accruals;
    private LoanAccountFakes.FakeAllocationRepository allocations;
    private LoanAccountFakes.FakeDpdSnapshotRepository dpdSnapshots;
    private LoanAccountFakes.RecordingPublisher publisher;
    private MutableCalendar calendar;
    private LoanAccountFakes.FakeProductCatalog catalog;
    private final ReducingBalanceScheduleEngine engine = new ReducingBalanceScheduleEngine();

    private CreateLoanService createService;
    private ActivateLoanService activateService;
    private AllocateRepaymentService allocateService;
    private PenalAccrualService penalService;
    private DpdService dpdService;
    private DueSoonService dueSoonService;

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    @BeforeEach
    void setUp() {
        loans = new LoanAccountFakes.FakeLoanRepository();
        schedules = new LoanAccountFakes.FakeScheduleRepository();
        history = new LoanAccountFakes.FakeStateHistoryRepository();
        accruals = new LoanAccountFakes.FakeAccrualRepository();
        allocations = new LoanAccountFakes.FakeAllocationRepository();
        dpdSnapshots = new LoanAccountFakes.FakeDpdSnapshotRepository();
        publisher = new LoanAccountFakes.RecordingPublisher();
        calendar = new MutableCalendar();
        catalog = new LoanAccountFakes.FakeProductCatalog();

        createService =
                new CreateLoanService(
                        loans, schedules, history, catalog, engine, publisher, calendar, CLOCK);
        activateService =
                new ActivateLoanService(
                        loans, schedules, history, catalog, engine, publisher, CLOCK);
        allocateService =
                new AllocateRepaymentService(
                        loans, schedules, allocations, history, publisher, calendar, CLOCK);
        penalService = new PenalAccrualService(loans, schedules, accruals, catalog, publisher);
        dpdService =
                new DpdService(loans, schedules, dpdSnapshots, history, catalog, publisher, CLOCK);
        dueSoonService = new DueSoonService(loans, schedules, accruals, publisher, 3);
    }

    private Loan activeLoan() {
        AcceptedApplication accepted =
                new AcceptedApplication(
                        "app-1",
                        "cust-1",
                        "MICRO-V1",
                        inr(5_000_000L),
                        12,
                        inr(474_000L),
                        inr(100_000L),
                        inr(18_000L),
                        inr(4_882_000L));
        Loan loan = createService.create(accepted, UUID.randomUUID());
        activateService.activate(loan.id(), inr(4_882_000L), DISBURSED, UUID.randomUUID());
        return loan;
    }

    private LocalDate firstDue(Loan loan) {
        return schedules.findByLoanId(loan.id()).get(0).dueDate();
    }

    @Test
    void penalAccruesOnlyAfterGraceAndIsIdempotentPerDate() {
        Loan loan = activeLoan();
        LocalDate firstDue = firstDue(loan);

        // Within grace (due + 3): no penal.
        assertThat(penalService.runFor(firstDue.plusDays(3))).isZero();

        // Past grace (due + 4): penal accrues once; a re-run for the same date does nothing.
        LocalDate pastGrace = firstDue.plusDays(4);
        assertThat(penalService.runFor(pastGrace)).isEqualTo(1);
        assertThat(penalService.runFor(pastGrace)).isZero();

        assertThat(publisher.events).filteredOn(e -> e.startsWith("loan.penal-accrued")).hasSize(1);
        // The penal landed on the oldest overdue instalment.
        LoanScheduleLine first = schedules.findByLoanId(loan.id()).get(0);
        assertThat(first.penalAccrued().isPositive()).isTrue();
        assertThat(accruals.totalPenalAccrued(loan.id()).isPositive()).isTrue();
    }

    @Test
    void dpdMarksDelinquentPastGraceAndPublishesTick() {
        Loan loan = activeLoan();
        LocalDate pastGrace = firstDue(loan).plusDays(4); // dpd 4 > grace 3

        assertThat(dpdService.runFor(pastGrace)).isEqualTo(1);
        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Delinquent());
        assertThat(publisher.events).filteredOn(e -> e.startsWith("loan.dpd-updated")).hasSize(1);

        // Idempotent: a second run for the same business date does nothing.
        assertThat(dpdService.runFor(pastGrace)).isZero();
    }

    @Test
    void clearingOverdueReturnsToActiveAndPublishesOneFinalTick() {
        Loan loan = activeLoan();
        LocalDate firstDue = firstDue(loan);
        LocalDate pastGrace = firstDue.plusDays(4);

        dpdService.runFor(pastGrace);
        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Delinquent());

        // Pay the first instalment in full while it is due.
        calendar.date = pastGrace;
        Money firstInstalment = schedules.findByLoanId(loan.id()).get(0).scheduledTotal();
        // Include any accrued penal so the instalment is fully settled.
        penalService.runFor(pastGrace);
        Money penal = schedules.findByLoanId(loan.id()).get(0).penalOutstanding();
        allocateService.allocate(loan.id(), "rep-1", firstInstalment.add(penal), UUID.randomUUID());

        // Next day: nothing overdue -> back to Active, and dpd-updated fires once more (was > 0).
        LocalDate nextDay = pastGrace.plusDays(1);
        long ticksBefore =
                publisher.events.stream().filter(e -> e.startsWith("loan.dpd-updated")).count();
        assertThat(dpdService.runFor(nextDay)).isEqualTo(1);
        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Active());
        long ticksAfter =
                publisher.events.stream().filter(e -> e.startsWith("loan.dpd-updated")).count();
        assertThat(ticksAfter).isEqualTo(ticksBefore + 1);
        assertThat(publisher.events).contains("loan.dpd-updated:" + nextDay + ":0");
    }

    @Test
    void dueSoonPublishesThreeBusinessDaysAheadOnce() {
        Loan loan = activeLoan();
        LocalDate firstDue = firstDue(loan);
        LocalDate threeBefore = firstDue.minusDays(3);

        assertThat(dueSoonService.runFor(threeBefore)).isEqualTo(1);
        assertThat(dueSoonService.runFor(threeBefore)).isZero(); // idempotent
        assertThat(publisher.events)
                .filteredOn(e -> e.startsWith("loan.installment-due-soon"))
                .hasSize(1);

        // Not published on an unrelated date.
        assertThat(dueSoonService.runFor(firstDue.minusDays(5))).isZero();
    }

    @Test
    void currentLoanNeverAccruesPenalOrGoesDelinquent() {
        Loan loan = activeLoan();
        LocalDate beforeDue = firstDue(loan).minusDays(1);

        assertThat(penalService.runFor(beforeDue)).isZero();
        assertThat(dpdService.runFor(beforeDue)).isEqualTo(1); // processed, but dpd 0
        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Active());
        // No tick published while current (dpd 0 and no prior positive dpd).
        assertThat(publisher.events).noneMatch(e -> e.startsWith("loan.dpd-updated"));
    }
}
