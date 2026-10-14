package com.lms.loanaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.ReducingBalanceScheduleEngine;
import com.lms.schedule.Schedule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Activate / allocate / accrue use cases (phase prompt task 2), proven with in-memory fakes. */
class LoanServicesTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-13T06:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DISBURSED = LocalDate.of(2026, 10, 13);

    /** Business date the tests can move forward. */
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
    private LoanAccountFakes.RecordingPublisher publisher;
    private MutableCalendar calendar;
    private final ReducingBalanceScheduleEngine engine = new ReducingBalanceScheduleEngine();

    private CreateLoanService createService;
    private ActivateLoanService activateService;
    private AllocateRepaymentService allocateService;
    private AccrueInterestService accrueService;

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
        publisher = new LoanAccountFakes.RecordingPublisher();
        calendar = new MutableCalendar();
        LoanAccountFakes.FakeProductCatalog catalog = new LoanAccountFakes.FakeProductCatalog();

        createService =
                new CreateLoanService(
                        loans, schedules, history, catalog, engine, publisher, calendar, CLOCK);
        activateService =
                new ActivateLoanService(
                        loans, schedules, history, catalog, engine, publisher, CLOCK);
        allocateService =
                new AllocateRepaymentService(
                        loans, schedules, allocations, history, publisher, calendar, CLOCK);
        accrueService = new AccrueInterestService(loans, schedules, accruals, publisher);
    }

    private Loan createdLoan() {
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
        return createService.create(accepted, UUID.randomUUID());
    }

    @Test
    void activatePersistsTheRealScheduleFromTheDisbursementDate() {
        Loan loan = createdLoan();

        Loan active =
                activateService.activate(loan.id(), inr(4_882_000L), DISBURSED, UUID.randomUUID());

        assertThat(active.state()).isEqualTo(new LoanState.Active());
        assertThat(active.disbursementDate()).isEqualTo(DISBURSED);

        Schedule expected =
                engine.generate(inr(5_000_000L), Rate.percent(24), 12, DISBURSED, false);
        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        assertThat(lines).hasSize(12).noneMatch(LoanScheduleLine::provisional);
        for (int i = 0; i < 12; i++) {
            assertThat(lines.get(i).dueDate()).isEqualTo(expected.items().get(i).dueDate());
            assertThat(lines.get(i).principal()).isEqualTo(expected.items().get(i).principal());
            assertThat(lines.get(i).interest()).isEqualTo(expected.items().get(i).interest());
        }
        assertThat(publisher.events).contains("loan.activated:" + loan.loanNumber());
    }

    @Test
    void activateRejectsAMismatchedDisbursedAmountAndAuditsIt() {
        Loan loan = createdLoan();

        assertThatThrownBy(
                        () ->
                                activateService.activate(
                                        loan.id(), inr(4_000_000L), DISBURSED, UUID.randomUUID()))
                .isInstanceOf(LoanTransitionRejectedException.class);

        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.PendingDisbursal());
        assertThat(history.entries).anyMatch(e -> !e.legal());
    }

    @Test
    void activateIsIdempotent() {
        Loan loan = createdLoan();
        activateService.activate(loan.id(), inr(4_882_000L), DISBURSED, UUID.randomUUID());
        long activatedEvents =
                publisher.events.stream().filter(e -> e.startsWith("loan.activated")).count();

        activateService.activate(loan.id(), inr(4_882_000L), DISBURSED, UUID.randomUUID());

        assertThat(publisher.events.stream().filter(e -> e.startsWith("loan.activated")).count())
                .isEqualTo(activatedEvents);
    }

    @Test
    void allocatePaysOffTheFirstInstalmentAndRecordsRows() {
        Loan loan = createdLoan();
        activateService.activate(loan.id(), inr(4_882_000L), DISBURSED, UUID.randomUUID());
        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        Money firstInstalment = lines.get(0).scheduledTotal();
        calendar.date = lines.get(0).dueDate();

        allocateService.allocate(loan.id(), "rep-1", firstInstalment, UUID.randomUUID());

        LoanScheduleLine first = schedules.findByLoanId(loan.id()).get(0);
        assertThat(first.isFullyPaid()).isTrue();
        assertThat(allocations.saved).isNotEmpty();
        assertThat(allocations.lastRepaymentId).isEqualTo("rep-1");
        assertThat(publisher.events).contains("loan.repayment-allocated:rep-1");
    }

    @Test
    void payingTheFullOutstandingClosesTheLoan() {
        Loan loan = createdLoan();
        activateService.activate(loan.id(), inr(4_882_000L), DISBURSED, UUID.randomUUID());
        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        Money totalPayable =
                lines.stream().map(LoanScheduleLine::scheduledTotal).reduce(inr(0), Money::add);
        calendar.date = lines.get(lines.size() - 1).dueDate(); // all instalments due

        allocateService.allocate(loan.id(), "rep-full", totalPayable, UUID.randomUUID());

        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Closed());
        assertThat(publisher.events).contains("loan.closed:" + loan.loanNumber() + ":PAID_IN_FULL");
    }

    @Test
    void accrualIsIdempotentPerDate() {
        Loan loan = createdLoan();
        activateService.activate(loan.id(), inr(4_882_000L), DISBURSED, UUID.randomUUID());
        LocalDate day = DISBURSED.plusDays(1);

        int first = accrueService.runFor(day);
        int second = accrueService.runFor(day);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0); // already accrued for that date
        long accrualEvents =
                publisher.events.stream()
                        .filter(e -> e.startsWith("loan.interest-accrued"))
                        .count();
        assertThat(accrualEvents).isEqualTo(1);
    }

    @Test
    void accrualOverTheWholeLifeEqualsScheduledInterest() {
        Loan loan = createdLoan();
        activateService.activate(loan.id(), inr(4_882_000L), DISBURSED, UUID.randomUUID());
        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        Money scheduledInterest =
                lines.stream().map(LoanScheduleLine::interest).reduce(inr(0), Money::add);
        LocalDate maturity = lines.get(lines.size() - 1).dueDate();

        for (LocalDate d = DISBURSED.plusDays(1); !d.isAfter(maturity); d = d.plusDays(1)) {
            accrueService.runFor(d);
        }

        assertThat(accruals.totalInterestAccrued(loan.id())).isEqualTo(scheduledInterest);
    }
}
