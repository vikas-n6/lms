package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
import com.lms.loanaccount.domain.LoanStateMachine;
import com.lms.loanaccount.domain.LoanTransition.ConfirmDisbursement;
import com.lms.loanaccount.domain.LoanTransitionResult;
import com.lms.money.Money;
import com.lms.schedule.Product;
import com.lms.schedule.Schedule;
import com.lms.schedule.ScheduleEngine;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@code disbursement.succeeded} (and the dev simulate endpoint): guards that the disbursed
 * net matches the loan's {@code principal − fee − GST}, generates the real schedule from the actual
 * disbursement date, replaces the provisional schedule, moves to {@link LoanState.Active} and
 * publishes {@code loan.activated} (phase prompt task 2). Idempotent: a loan already Active is
 * returned unchanged.
 */
public class ActivateLoanService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final StateHistoryRepository history;
    private final ProductCatalog products;
    private final ScheduleEngine scheduleEngine;
    private final LoanEventPublisher publisher;
    private final Clock clock;

    public ActivateLoanService(
            LoanRepository loans,
            ScheduleRepository schedules,
            StateHistoryRepository history,
            ProductCatalog products,
            ScheduleEngine scheduleEngine,
            LoanEventPublisher publisher,
            Clock clock) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.history = Objects.requireNonNull(history);
        this.products = Objects.requireNonNull(products);
        this.scheduleEngine = Objects.requireNonNull(scheduleEngine);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public Loan activate(
            UUID loanId, Money disbursedNet, LocalDate disbursementDate, UUID sourceEventId) {
        Objects.requireNonNull(disbursedNet, "disbursedNet");
        Objects.requireNonNull(disbursementDate, "disbursementDate");
        Loan loan = loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        if (loan.state() instanceof LoanState.Active) {
            return loan; // idempotent re-delivery
        }

        boolean amountMatches = disbursedNet.equals(loan.netDisbursal());
        LoanTransitionResult result =
                LoanStateMachine.transition(loan.state(), new ConfirmDisbursement(amountMatches));
        if (!(result instanceof LoanTransitionResult.Moved moved)) {
            LoanTransitionResult.Illegal illegal = (LoanTransitionResult.Illegal) result;
            history.append(
                    loanId,
                    loan.state(),
                    loan.state(),
                    "disbursement.succeeded",
                    sourceEventId,
                    false);
            throw new LoanTransitionRejectedException(illegal);
        }

        Product product = products.byVersion(loan.productId(), loan.productVersion());
        Schedule real =
                scheduleEngine.generate(
                        loan.principal(),
                        product.annualInterestRate(),
                        loan.tenorMonths(),
                        disbursementDate,
                        false);
        List<LoanScheduleLine> lines = ScheduleLines.from(loanId, real, false);

        Loan active = loan.activated(disbursementDate, clock.instant());
        loans.update(active);
        schedules.replaceSchedule(loanId, lines);
        history.append(
                loanId, moved.from(), moved.to(), "disbursement.succeeded", sourceEventId, true);
        publisher.loanActivated(
                active,
                lines.get(0).dueDate(),
                lines.get(lines.size() - 1).dueDate(),
                sourceEventId);
        return active;
    }
}
