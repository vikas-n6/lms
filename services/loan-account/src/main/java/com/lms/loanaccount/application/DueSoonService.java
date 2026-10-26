package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.AccrualRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The pre-due reminder job (phase prompt task 1): publishes {@code loan.installment-due-soon} a
 * configurable number of business days (default 3) before each instalment falls due, so
 * notification can remind the customer. Idempotent per {@code (loanId, businessDate)} via the
 * {@code accruals} table ({@code kind=DUE_SOON}), so a re-run sends no second reminder. Business
 * days come from the caller's business date, never the system clock.
 */
public class DueSoonService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final AccrualRepository accruals;
    private final LoanEventPublisher publisher;
    private final int preDueDays;

    public DueSoonService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AccrualRepository accruals,
            LoanEventPublisher publisher,
            int preDueDays) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.accruals = Objects.requireNonNull(accruals);
        this.publisher = Objects.requireNonNull(publisher);
        if (preDueDays < 0) {
            throw new IllegalArgumentException("preDueDays must be non-negative");
        }
        this.preDueDays = preDueDays;
    }

    /** Publish due-soon reminders for instalments falling due {@code preDueDays} ahead. */
    public int runFor(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate");
        LocalDate target = businessDate.plusDays(preDueDays);
        int sent = 0;
        for (Loan loan : accruingLoans()) {
            for (LoanScheduleLine line : schedules.findByLoanId(loan.id())) {
                if (line.isFullyPaid() || !line.dueDate().equals(target)) {
                    continue;
                }
                if (accruals.recordDueSoonSent(loan.id(), businessDate, line.instalmentNumber())) {
                    publisher.installmentDueSoon(
                            loan.id(),
                            loan.loanNumber(),
                            line.instalmentNumber(),
                            line.dueDate(),
                            line.scheduledTotal());
                    sent++;
                }
            }
        }
        return sent;
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
