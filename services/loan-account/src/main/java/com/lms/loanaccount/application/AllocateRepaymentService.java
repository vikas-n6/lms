package com.lms.loanaccount.application;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.port.out.AllocationRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Allocation;
import com.lms.loanaccount.domain.AllocationResult;
import com.lms.loanaccount.domain.AllocationWaterfall;
import com.lms.loanaccount.domain.Component;
import com.lms.loanaccount.domain.InstalmentDue;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanStateMachine;
import com.lms.loanaccount.domain.LoanTransition.PayInFull;
import com.lms.loanaccount.domain.LoanTransitionResult;
import com.lms.money.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@code repayment.received}: runs the allocation waterfall in one transaction, applies the
 * component amounts to the schedule items, stores the allocation rows and publishes {@code
 * loan.repayment-allocated}; if the outstanding total then reaches zero the loan is closed and
 * {@code loan.closed} is published (phase prompt task 2, docs/domain-rules.md "Repayment allocation
 * waterfall"). Only instalments due on or before the business date participate; any excess is held
 * as advance in Customer suspense.
 */
public class AllocateRepaymentService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final AllocationRepository allocations;
    private final StateHistoryRepository history;
    private final LoanEventPublisher publisher;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public AllocateRepaymentService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AllocationRepository allocations,
            StateHistoryRepository history,
            LoanEventPublisher publisher,
            BusinessCalendar calendar,
            Clock clock) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.allocations = Objects.requireNonNull(allocations);
        this.history = Objects.requireNonNull(history);
        this.publisher = Objects.requireNonNull(publisher);
        this.calendar = Objects.requireNonNull(calendar);
        this.clock = Objects.requireNonNull(clock);
    }

    public AllocationResult allocate(
            UUID loanId, String repaymentId, Money amount, UUID sourceEventId) {
        Objects.requireNonNull(repaymentId, "repaymentId");
        Objects.requireNonNull(amount, "amount");
        Loan loan = loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        LocalDate businessDate = calendar.today();

        List<LoanScheduleLine> lines = new ArrayList<>(schedules.findByLoanId(loanId));
        lines.sort(Comparator.comparingInt(LoanScheduleLine::instalmentNumber));

        // Only instalments that have fallen due (on or before today) participate; the rest is
        // advance, held for when the next instalment falls due (docs/domain-rules.md).
        List<InstalmentDue> dues =
                lines.stream()
                        .filter(l -> !l.dueDate().isAfter(businessDate))
                        .filter(l -> !l.isFullyPaid())
                        .map(LoanScheduleLine::toDue)
                        .toList();

        AllocationResult result = AllocationWaterfall.allocate(dues, amount);

        applyToLines(lines, result.allocations());
        schedules.saveAll(lines);
        if (!result.allocations().isEmpty()) {
            allocations.saveAll(loanId, repaymentId, result.allocations());
        }
        publisher.repaymentAllocated(loanId, loan.loanNumber(), repaymentId, result, sourceEventId);

        closeIfFullyPaid(loan, lines, sourceEventId);
        return result;
    }

    private void applyToLines(List<LoanScheduleLine> lines, List<Allocation> allocationRows) {
        Map<Integer, List<Allocation>> byInstalment = new java.util.LinkedHashMap<>();
        for (Allocation a : allocationRows) {
            byInstalment.computeIfAbsent(a.instalmentNumber(), k -> new ArrayList<>()).add(a);
        }
        for (int i = 0; i < lines.size(); i++) {
            LoanScheduleLine line = lines.get(i);
            List<Allocation> rows = byInstalment.get(line.instalmentNumber());
            if (rows == null) {
                continue;
            }
            Money zero = Money.zero(line.principal().currency());
            Money penal = zero;
            Money interest = zero;
            Money principal = zero;
            for (Allocation a : rows) {
                if (a.component() == Component.PENAL) {
                    penal = penal.add(a.amount());
                } else if (a.component() == Component.INTEREST) {
                    interest = interest.add(a.amount());
                } else {
                    principal = principal.add(a.amount());
                }
            }
            lines.set(i, line.applyPayment(penal, interest, principal));
        }
    }

    private void closeIfFullyPaid(Loan loan, List<LoanScheduleLine> lines, UUID sourceEventId) {
        boolean allPaid =
                !lines.isEmpty() && lines.stream().allMatch(LoanScheduleLine::isFullyPaid);
        if (!allPaid) {
            return;
        }
        LoanTransitionResult result =
                LoanStateMachine.transition(loan.state(), new PayInFull(true));
        if (result instanceof LoanTransitionResult.Moved moved) {
            Loan closed = loan.withState(moved.to(), clock.instant());
            loans.update(closed);
            history.append(
                    loan.id(), moved.from(), moved.to(), "repayment.received", sourceEventId, true);
            publisher.loanClosed(closed, "PAID_IN_FULL", sourceEventId);
        }
    }
}
