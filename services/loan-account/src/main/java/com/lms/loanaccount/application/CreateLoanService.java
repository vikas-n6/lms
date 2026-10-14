package com.lms.loanaccount.application;

import com.lms.config.BusinessCalendar;
import com.lms.events.Uuid7;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanNumber;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
import com.lms.loanaccount.domain.LoanStateMachine;
import com.lms.loanaccount.domain.LoanTransition.DraftSchedule;
import com.lms.loanaccount.domain.LoanTransitionResult;
import com.lms.schedule.Product;
import com.lms.schedule.Schedule;
import com.lms.schedule.ScheduleEngine;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@code application.accepted}: creates a loan in {@link LoanState.Created}, pins the
 * current product version and accepted terms, drafts a provisional schedule, moves to {@link
 * LoanState.PendingDisbursal} and publishes {@code loan.created} (phase prompt task 2). Idempotent
 * on {@code applicationId} — a duplicate delivery returns the existing loan and publishes nothing.
 */
public class CreateLoanService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final StateHistoryRepository history;
    private final ProductCatalog products;
    private final ScheduleEngine scheduleEngine;
    private final LoanEventPublisher publisher;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public CreateLoanService(
            LoanRepository loans,
            ScheduleRepository schedules,
            StateHistoryRepository history,
            ProductCatalog products,
            ScheduleEngine scheduleEngine,
            LoanEventPublisher publisher,
            BusinessCalendar calendar,
            Clock clock) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.history = Objects.requireNonNull(history);
        this.products = Objects.requireNonNull(products);
        this.scheduleEngine = Objects.requireNonNull(scheduleEngine);
        this.publisher = Objects.requireNonNull(publisher);
        this.calendar = Objects.requireNonNull(calendar);
        this.clock = Objects.requireNonNull(clock);
    }

    public Loan create(AcceptedApplication accepted, UUID sourceEventId) {
        Objects.requireNonNull(accepted, "accepted");
        var existing = loans.findByApplicationId(accepted.applicationId());
        if (existing.isPresent()) {
            return existing.get(); // idempotent: one loan per application
        }

        Product product = products.current();
        var now = clock.instant();
        LocalDate today = calendar.today();
        long sequence = loans.nextLoanSequence();
        String loanNumber = LoanNumber.format(today.getYear(), sequence);
        UUID loanId = Uuid7.generate(clock);

        Loan created =
                Loan.create(
                        loanId,
                        loanNumber,
                        accepted.applicationId(),
                        accepted.customerId(),
                        product.productId(),
                        product.version(),
                        accepted.principal(),
                        accepted.tenorMonths(),
                        accepted.emi(),
                        accepted.fee(),
                        accepted.gst(),
                        accepted.netDisbursal(),
                        product.annualInterestRate(),
                        now);

        // Draft the provisional (indicative) schedule off the current business date.
        Schedule provisional =
                scheduleEngine.generate(
                        accepted.principal(),
                        product.annualInterestRate(),
                        accepted.tenorMonths(),
                        today,
                        true);
        List<LoanScheduleLine> lines = ScheduleLines.from(loanId, provisional, true);

        LoanTransitionResult result =
                LoanStateMachine.transition(created.state(), new DraftSchedule(true));
        if (!(result instanceof LoanTransitionResult.Moved moved)) {
            throw new IllegalStateException("could not draft schedule: " + result);
        }
        Loan pending = created.withState(moved.to(), now);

        loans.insert(pending);
        schedules.replaceSchedule(loanId, lines);
        history.append(
                loanId, null, new LoanState.Created(), "application.accepted", sourceEventId, true);
        history.append(loanId, moved.from(), moved.to(), "schedule drafted", sourceEventId, true);
        publisher.loanCreated(pending, sourceEventId);
        return pending;
    }
}
