package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.DpdSnapshotRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Delinquency;
import com.lms.loanaccount.domain.DelinquencySnapshot;
import com.lms.loanaccount.domain.DpdSnapshot;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
import com.lms.loanaccount.domain.LoanStateMachine;
import com.lms.loanaccount.domain.LoanTransition.ClearOverdue;
import com.lms.loanaccount.domain.LoanTransition.MarkDelinquent;
import com.lms.loanaccount.domain.LoanTransitionResult;
import com.lms.schedule.Product;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The daily DPD job (phase prompt task 1). For each accruing loan it computes the {@link
 * DelinquencySnapshot}, moves the loan between {@code Active} and {@code Delinquent} via the pure
 * {@link LoanStateMachine} (recording every transition in {@code loan_state_history}), and
 * publishes {@code loan.dpd-updated} for every loan with DPD above zero, plus once when DPD returns
 * to zero. Idempotent per {@code (loanId, businessDate)}: a re-run for a date already recorded does
 * nothing.
 */
public class DpdService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final DpdSnapshotRepository snapshots;
    private final StateHistoryRepository history;
    private final ProductCatalog products;
    private final LoanEventPublisher publisher;
    private final Clock clock;

    public DpdService(
            LoanRepository loans,
            ScheduleRepository schedules,
            DpdSnapshotRepository snapshots,
            StateHistoryRepository history,
            ProductCatalog products,
            LoanEventPublisher publisher,
            Clock clock) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.history = Objects.requireNonNull(history);
        this.products = Objects.requireNonNull(products);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Compute DPD for all accruing loans on {@code businessDate}. Returns the number processed. */
    public int runFor(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate");
        int processed = 0;
        for (Loan loan : accruingLoans()) {
            if (process(loan, businessDate)) {
                processed++;
            }
        }
        return processed;
    }

    private boolean process(Loan loan, LocalDate businessDate) {
        Optional<DpdSnapshot> previous = snapshots.find(loan.id());
        if (previous.map(p -> p.businessDate().equals(businessDate)).orElse(false)) {
            return false; // already computed for this business date: idempotent
        }
        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        Product product = products.byVersion(loan.productId(), loan.productVersion());
        DelinquencySnapshot snapshot =
                Delinquency.assess(
                        lines, loan.principal().currency(), businessDate, product.graceDays());

        transitionIfNeeded(loan, snapshot, product.graceDays(), businessDate);

        boolean previousPositive = previous.map(p -> p.daysPastDue() > 0).orElse(false);
        if (snapshot.daysPastDue() > 0 || previousPositive) {
            publisher.dpdUpdated(loan.id(), loan.loanNumber(), businessDate, snapshot);
        }
        snapshots.upsert(
                new DpdSnapshot(
                        loan.id(), businessDate, snapshot.daysPastDue(), snapshot.bucket().name()));
        return true;
    }

    private void transitionIfNeeded(
            Loan loan, DelinquencySnapshot snapshot, int graceDays, LocalDate businessDate) {
        String cause = "dpd-job:" + businessDate;
        if (loan.state() instanceof LoanState.Active
                && snapshot.daysPastDue() > graceDays
                && snapshot.overdueInstalmentCount() >= 1) {
            apply(loan, new MarkDelinquent(snapshot.overdueInstalmentCount()), cause);
        } else if (loan.state() instanceof LoanState.Delinquent
                && snapshot.overdueTotal().isZero()) {
            apply(loan, new ClearOverdue(true), cause);
        }
    }

    private void apply(Loan loan, com.lms.loanaccount.domain.LoanTransition trigger, String cause) {
        LoanTransitionResult result = LoanStateMachine.transition(loan.state(), trigger);
        if (result instanceof LoanTransitionResult.Moved moved) {
            loans.update(loan.withState(moved.to(), clock.instant()));
            history.append(loan.id(), moved.from(), moved.to(), cause, null, true);
        } else if (result instanceof LoanTransitionResult.Illegal illegal) {
            // Record the rejected transition; never silently ignore it (docs/domain-rules.md).
            history.append(loan.id(), loan.state(), loan.state(), illegal.reason(), null, false);
        }
    }

    private List<Loan> accruingLoans() {
        Stream<Loan> active = loans.findAll(Optional.of("ACTIVE"), Optional.empty()).stream();
        Stream<Loan> delinquent =
                loans.findAll(Optional.of("DELINQUENT"), Optional.empty()).stream();
        return Stream.concat(active, delinquent)
                .filter(l -> l.disbursementDate() != null)
                .sorted(Comparator.comparing(Loan::loanNumber))
                .toList();
    }
}
