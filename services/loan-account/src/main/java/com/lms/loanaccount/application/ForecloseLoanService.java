package com.lms.loanaccount.application;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.port.out.AllocationRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Allocation;
import com.lms.loanaccount.domain.AllocationResult;
import com.lms.loanaccount.domain.Component;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanStateMachine;
import com.lms.loanaccount.domain.LoanTransition.Foreclose;
import com.lms.loanaccount.domain.LoanTransitionResult;
import com.lms.money.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles a foreclosure repayment ({@code repayment.received} with {@code purpose=FORECLOSURE}): if
 * the amount is at least the foreclosure quote at the business date, it clears every instalment,
 * moves the loan {@code Active|Delinquent → Foreclosed} (docs/domain-rules.md "Loan state machine")
 * and publishes {@code loan.repayment-allocated} + {@code loan.foreclosed}. A short payment falls
 * back to the ordinary allocation waterfall. The actual/365 interest and charge beyond the
 * scheduled components are a documented simplification (ADR 0005) and remain as advance in
 * suspense.
 */
public class ForecloseLoanService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final AllocationRepository allocations;
    private final StateHistoryRepository history;
    private final LoanEventPublisher publisher;
    private final LoanQueryService queryService;
    private final AllocateRepaymentService allocateService;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public ForecloseLoanService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AllocationRepository allocations,
            StateHistoryRepository history,
            LoanEventPublisher publisher,
            LoanQueryService queryService,
            AllocateRepaymentService allocateService,
            BusinessCalendar calendar,
            Clock clock) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.allocations = Objects.requireNonNull(allocations);
        this.history = Objects.requireNonNull(history);
        this.publisher = Objects.requireNonNull(publisher);
        this.queryService = Objects.requireNonNull(queryService);
        this.allocateService = Objects.requireNonNull(allocateService);
        this.calendar = Objects.requireNonNull(calendar);
        this.clock = Objects.requireNonNull(clock);
    }

    public void foreclose(UUID loanId, String repaymentId, Money amount, UUID sourceEventId) {
        Objects.requireNonNull(repaymentId, "repaymentId");
        Objects.requireNonNull(amount, "amount");
        Loan loan = loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        LocalDate businessDate = calendar.today();
        Money quote = queryService.foreclosureQuote(loanId, businessDate);

        if (amount.compareTo(quote) < 0) {
            // not enough to foreclose: treat as an ordinary repayment down the waterfall.
            allocateService.allocate(loanId, repaymentId, amount, sourceEventId);
            return;
        }

        List<LoanScheduleLine> lines = new ArrayList<>(schedules.findByLoanId(loanId));
        lines.sort(Comparator.comparingInt(LoanScheduleLine::instalmentNumber));
        Money currencyZero = Money.zero(loan.principal().currency());

        // Foreclosure clears all outstanding principal, plus interest/penal already on the books
        // for
        // instalments due on or before the business date; future scheduled interest is waived (you
        // do not pay interest you have not yet incurred). The quote's actual/365 accrued interest
        // and
        // charge beyond these components are the documented simplification (ADR 0005), reported as
        // advance.
        List<Allocation> rows = new ArrayList<>();
        Money penalTotal = currencyZero;
        Money interestTotal = currencyZero;
        Money principalTotal = currencyZero;
        for (int i = 0; i < lines.size(); i++) {
            LoanScheduleLine line = lines.get(i);
            boolean due = !line.dueDate().isAfter(businessDate);
            Money penal = due ? line.penalOutstanding() : currencyZero;
            Money interest = due ? line.interestOutstanding() : currencyZero;
            Money principal = line.principalOutstanding();
            addRow(rows, line.instalmentNumber(), Component.PENAL, penal);
            addRow(rows, line.instalmentNumber(), Component.INTEREST, interest);
            addRow(rows, line.instalmentNumber(), Component.PRINCIPAL, principal);
            penalTotal = penalTotal.add(penal);
            interestTotal = interestTotal.add(interest);
            principalTotal = principalTotal.add(principal);
            lines.set(i, line.applyPayment(penal, interest, principal));
        }
        schedules.saveAll(lines);
        if (!rows.isEmpty()) {
            allocations.saveAll(loanId, repaymentId, rows);
        }

        Money clearedTotal = penalTotal.add(interestTotal).add(principalTotal);
        Money advance = amount.subtract(clearedTotal); // actual/365 interest + charge + overpayment
        AllocationResult result =
                new AllocationResult(rows, penalTotal, interestTotal, principalTotal, advance);
        publisher.repaymentAllocated(loanId, loan.loanNumber(), repaymentId, result, sourceEventId);

        LoanTransitionResult transition =
                LoanStateMachine.transition(loan.state(), new Foreclose(true));
        if (transition instanceof LoanTransitionResult.Moved moved) {
            Loan foreclosed = loan.withState(moved.to(), clock.instant());
            loans.update(foreclosed);
            history.append(
                    loan.id(), moved.from(), moved.to(), "repayment.received", sourceEventId, true);
            publisher.loanForeclosed(foreclosed, repaymentId, quote, sourceEventId);
        } else {
            history.append(
                    loan.id(),
                    loan.state(),
                    loan.state(),
                    "repayment.received",
                    sourceEventId,
                    false);
        }
    }

    private static void addRow(
            List<Allocation> rows, int instalment, Component component, Money m) {
        if (m.isPositive()) {
            rows.add(new Allocation(instalment, component, m));
        }
    }
}
