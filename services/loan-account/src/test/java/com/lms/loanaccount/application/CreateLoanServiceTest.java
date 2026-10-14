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
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateLoanServiceTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-13T06:00:00Z"), ZoneOffset.UTC);
    private static final BusinessCalendar CALENDAR = () -> java.time.LocalDate.of(2026, 10, 13);

    private LoanAccountFakes.FakeLoanRepository loans;
    private LoanAccountFakes.FakeScheduleRepository schedules;
    private LoanAccountFakes.FakeStateHistoryRepository history;
    private LoanAccountFakes.RecordingPublisher publisher;
    private CreateLoanService service;

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    private static AcceptedApplication accepted(String applicationId) {
        return new AcceptedApplication(
                applicationId,
                "cust-1",
                "MICRO-V1",
                inr(5_000_000L),
                12,
                inr(474_000L),
                inr(100_000L),
                inr(18_000L),
                inr(4_882_000L));
    }

    @BeforeEach
    void setUp() {
        loans = new LoanAccountFakes.FakeLoanRepository();
        schedules = new LoanAccountFakes.FakeScheduleRepository();
        history = new LoanAccountFakes.FakeStateHistoryRepository();
        publisher = new LoanAccountFakes.RecordingPublisher();
        service =
                new CreateLoanService(
                        loans,
                        schedules,
                        history,
                        new LoanAccountFakes.FakeProductCatalog(),
                        new ReducingBalanceScheduleEngine(),
                        publisher,
                        CALENDAR,
                        CLOCK);
    }

    @Test
    void createsAPendingDisbursalLoanWithAProvisionalScheduleAndPublishesLoanCreated() {
        Loan loan = service.create(accepted("app-1"), UUID.randomUUID());

        assertThat(loan.state()).isEqualTo(new LoanState.PendingDisbursal());
        assertThat(loan.loanNumber()).isEqualTo("LN-2026-000001");
        assertThat(loan.productVersion()).isEqualTo(1);
        assertThat(loan.principal()).isEqualTo(inr(5_000_000L));

        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        assertThat(lines).hasSize(12);
        assertThat(lines).allMatch(LoanScheduleLine::provisional);
        // Principal portions of the provisional schedule sum to the principal.
        Money principalSum =
                lines.stream().map(LoanScheduleLine::principal).reduce(inr(0), Money::add);
        assertThat(principalSum).isEqualTo(inr(5_000_000L));

        assertThat(publisher.events).containsExactly("loan.created:LN-2026-000001");
        // State history: initial Created, then Created -> PendingDisbursal.
        assertThat(history.entries).hasSize(2);
        assertThat(history.entries.get(0).to()).isEqualTo(new LoanState.Created());
        assertThat(history.entries.get(1).from()).isEqualTo(new LoanState.Created());
        assertThat(history.entries.get(1).to()).isEqualTo(new LoanState.PendingDisbursal());
    }

    @Test
    void isIdempotentOnApplicationId() {
        UUID eventId = UUID.randomUUID();
        Loan first = service.create(accepted("app-1"), eventId);
        Loan second = service.create(accepted("app-1"), eventId);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(loans.byId).hasSize(1);
        // Only the first delivery publishes and records history.
        assertThat(publisher.events).containsExactly("loan.created:LN-2026-000001");
        assertThat(history.entries).hasSize(2);
    }
}
