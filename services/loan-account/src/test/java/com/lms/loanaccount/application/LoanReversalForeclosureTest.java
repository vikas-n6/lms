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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reversal (returned debit) and foreclosure-close use cases (phase prompt task 4). */
class LoanReversalForeclosureTest {

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
    private LoanAccountFakes.FakeAllocationRepository allocations;
    private LoanAccountFakes.RecordingPublisher publisher;
    private MutableCalendar calendar;
    private final ReducingBalanceScheduleEngine engine = new ReducingBalanceScheduleEngine();

    private CreateLoanService createService;
    private ActivateLoanService activateService;
    private AllocateRepaymentService allocateService;
    private ReverseAllocationService reverseService;
    private ForecloseLoanService forecloseService;
    private LoanQueryService queryService;

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    @BeforeEach
    void setUp() {
        loans = new LoanAccountFakes.FakeLoanRepository();
        schedules = new LoanAccountFakes.FakeScheduleRepository();
        history = new LoanAccountFakes.FakeStateHistoryRepository();
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
        reverseService = new ReverseAllocationService(loans, schedules, allocations, publisher);
        queryService = new LoanQueryService(loans, schedules, history, catalog, calendar);
        forecloseService =
                new ForecloseLoanService(
                        loans,
                        schedules,
                        allocations,
                        history,
                        publisher,
                        queryService,
                        allocateService,
                        calendar,
                        CLOCK);
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

    @Test
    void reversalReopensTheInstalmentAndRecordsReversingRows() {
        Loan loan = activeLoan();
        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        Money firstInstalment = lines.get(0).scheduledTotal();
        calendar.date = lines.get(0).dueDate();
        allocateService.allocate(loan.id(), "rep-1", firstInstalment, UUID.randomUUID());
        assertThat(schedules.findByLoanId(loan.id()).get(0).isFullyPaid()).isTrue();

        reverseService.reverse(loan.id(), "rep-1", UUID.randomUUID());

        LoanScheduleLine first = schedules.findByLoanId(loan.id()).get(0);
        assertThat(first.isFullyPaid()).isFalse(); // reopened
        assertThat(first.paidPrincipal().isZero()).isTrue();
        assertThat(first.paidInterest().isZero()).isTrue();
        assertThat(allocations.reversed).isNotEmpty();
        assertThat(allocations.lastReversalOf).isEqualTo("rep-1");
        assertThat(publisher.events).contains("loan.repayment-reversed:rep-1");
    }

    @Test
    void reversingAnUnknownRepaymentIsANoOp() {
        Loan loan = activeLoan();
        reverseService.reverse(loan.id(), "never", UUID.randomUUID());
        assertThat(allocations.reversed).isEmpty();
        assertThat(publisher.events).noneMatch(e -> e.startsWith("loan.repayment-reversed"));
    }

    @Test
    void foreclosurePayingTheQuoteClosesTheLoanAsForeclosed() {
        Loan loan = activeLoan();
        // Day 0: no instalment is due yet, so the quote is essentially the outstanding principal.
        Money quote = queryService.foreclosureQuote(loan.id(), calendar.date);

        forecloseService.foreclose(loan.id(), "rep-fc", quote, UUID.randomUUID());

        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Foreclosed());
        assertThat(publisher.events)
                .anyMatch(e -> e.startsWith("loan.foreclosed:" + loan.loanNumber()));
        // All principal is cleared across the schedule.
        assertThat(schedules.findByLoanId(loan.id()))
                .allMatch(l -> l.principalOutstanding().isZero());
    }

    @Test
    void foreclosureShortOfTheQuoteFallsBackToOrdinaryAllocation() {
        Loan loan = activeLoan();
        forecloseService.foreclose(loan.id(), "rep-partial", inr(1_000L), UUID.randomUUID());

        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Active());
        assertThat(publisher.events).contains("loan.repayment-allocated:rep-partial");
        assertThat(publisher.events).noneMatch(e -> e.startsWith("loan.foreclosed"));
    }
}
