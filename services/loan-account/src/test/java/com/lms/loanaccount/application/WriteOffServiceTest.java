package com.lms.loanaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanState;
import com.lms.loanaccount.domain.WriteOffRequest;
import com.lms.loanaccount.domain.WriteOffRequestStatus;
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
 * Two-person write-off (phase prompt task 2 acceptance tests): a request by admin A approved by
 * admin A is rejected (two-person rule); approval by admin B succeeds only at DPD 180 or more; and
 * after a successful write-off the loan is {@code WrittenOff}, its receivables are zero, and {@code
 * loan.written-off} is published with the outstanding components.
 */
class WriteOffServiceTest {

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
    private LoanAccountFakes.FakeDpdSnapshotRepository dpdSnapshots;
    private LoanAccountFakes.FakeWriteOffRequestRepository woRequests;
    private LoanAccountFakes.RecordingPublisher publisher;
    private MutableCalendar calendar;
    private final ReducingBalanceScheduleEngine engine = new ReducingBalanceScheduleEngine();

    private CreateLoanService createService;
    private ActivateLoanService activateService;
    private DpdService dpdService;
    private WriteOffService writeOffService;

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    @BeforeEach
    void setUp() {
        loans = new LoanAccountFakes.FakeLoanRepository();
        schedules = new LoanAccountFakes.FakeScheduleRepository();
        history = new LoanAccountFakes.FakeStateHistoryRepository();
        dpdSnapshots = new LoanAccountFakes.FakeDpdSnapshotRepository();
        woRequests = new LoanAccountFakes.FakeWriteOffRequestRepository();
        publisher = new LoanAccountFakes.RecordingPublisher();
        calendar = new MutableCalendar();
        LoanAccountFakes.FakeProductCatalog catalog = new LoanAccountFakes.FakeProductCatalog();

        createService =
                new CreateLoanService(
                        loans, schedules, history, catalog, engine, publisher, calendar, CLOCK);
        activateService =
                new ActivateLoanService(
                        loans, schedules, history, catalog, engine, publisher, CLOCK);
        dpdService =
                new DpdService(loans, schedules, dpdSnapshots, history, catalog, publisher, CLOCK);
        writeOffService =
                new WriteOffService(
                        loans,
                        schedules,
                        woRequests,
                        history,
                        catalog,
                        publisher,
                        calendar,
                        CLOCK,
                        entry -> {},
                        180);
    }

    /** An active loan driven to {@code Delinquent} at {@code businessDate} by the DPD job. */
    private Loan delinquentLoan(LocalDate businessDate) {
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
        calendar.date = businessDate;
        dpdService.runFor(businessDate);
        return loans.findById(loan.id()).orElseThrow();
    }

    @Test
    void sameUserCannotApproveOwnRequest() {
        // 2026-11-13 first due -> 2027-06-01 is ~200 DPD (>= 180) and Delinquent.
        Loan loan = delinquentLoan(LocalDate.of(2027, 6, 1));
        assertThat(loan.state()).isEqualTo(new LoanState.Delinquent());

        WriteOffRequest request =
                writeOffService.request(loan.id(), "uncollectable NPA", "admin-a");

        assertThatThrownBy(() -> writeOffService.approve(request.id(), "admin-a"))
                .isInstanceOf(WriteOffRequest.SelfApprovalException.class);
        // The loan is untouched and the request is still pending.
        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Delinquent());
        assertThat(woRequests.findById(request.id()).orElseThrow().status())
                .isEqualTo(WriteOffRequestStatus.REQUESTED);
    }

    @Test
    void approvalByDifferentAdminWritesLoanOffWithZeroReceivables() {
        Loan loan = delinquentLoan(LocalDate.of(2027, 6, 1));
        WriteOffRequest request =
                writeOffService.request(loan.id(), "uncollectable NPA", "admin-a");

        WriteOffRequest approved = writeOffService.approve(request.id(), "admin-b");

        assertThat(approved.status()).isEqualTo(WriteOffRequestStatus.APPROVED);
        assertThat(approved.decidedBy()).isEqualTo("admin-b");
        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.WrittenOff());
        // Receivables are zero after write-off.
        assertThat(schedules.findByLoanId(loan.id()))
                .allMatch(l -> l.principalOutstanding().isZero())
                .allMatch(l -> l.interestOutstanding().isZero())
                .allMatch(l -> l.penalOutstanding().isZero());
        assertThat(publisher.events)
                .anyMatch(e -> e.startsWith("loan.written-off:" + loan.loanNumber()));
    }

    @Test
    void approvalBelowDpdThresholdIsRejected() {
        // 2026-11-17 is only 4 DPD past the first due date: Delinquent but far below 180.
        Loan loan = delinquentLoan(LocalDate.of(2026, 11, 17));
        assertThat(loan.state()).isEqualTo(new LoanState.Delinquent());
        WriteOffRequest request = writeOffService.request(loan.id(), "too early", "admin-a");

        assertThatThrownBy(() -> writeOffService.approve(request.id(), "admin-b"))
                .isInstanceOf(WriteOffNotAllowedException.class);
        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Delinquent());
    }

    @Test
    void reasonIsRequiredToRaiseARequest() {
        Loan loan = delinquentLoan(LocalDate.of(2027, 6, 1));
        assertThatThrownBy(() -> writeOffService.request(loan.id(), "  ", "admin-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectedRequestLeavesTheLoanDelinquent() {
        Loan loan = delinquentLoan(LocalDate.of(2027, 6, 1));
        WriteOffRequest request = writeOffService.request(loan.id(), "review", "admin-a");

        WriteOffRequest rejected = writeOffService.reject(request.id(), "admin-b", "will retry");

        assertThat(rejected.status()).isEqualTo(WriteOffRequestStatus.REJECTED);
        assertThat(loans.findById(loan.id()).orElseThrow().state())
                .isEqualTo(new LoanState.Delinquent());
        assertThat(publisher.events).noneMatch(e -> e.startsWith("loan.written-off"));
    }
}
