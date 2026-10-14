package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.AccrualRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.domain.DailyInterest;
import com.lms.loanaccount.domain.InterestAccrual;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
import com.lms.money.Money;
import com.lms.schedule.ScheduleItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The daily interest-accrual job (phase prompt task 2). For each loan that is Active or Delinquent,
 * it recognises the interest for {@code businessDate} (docs/domain-rules.md "Interest and penal
 * accrual"), records it idempotently per {@code (loanId, businessDate)} and publishes {@code
 * loan.interest-accrued}. Running the job twice for one date records and publishes once. The job
 * takes the business date as a parameter, never reading the system clock.
 */
public class AccrueInterestService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final AccrualRepository accruals;
    private final LoanEventPublisher publisher;

    public AccrueInterestService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AccrualRepository accruals,
            LoanEventPublisher publisher) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.accruals = Objects.requireNonNull(accruals);
        this.publisher = Objects.requireNonNull(publisher);
    }

    /** Run accrual for all accruing loans on {@code businessDate}. Returns the number recorded. */
    public int runFor(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate");
        int recorded = 0;
        for (Loan loan : accruingLoans()) {
            if (accrueLoan(loan, businessDate)) {
                recorded++;
            }
        }
        return recorded;
    }

    private List<Loan> accruingLoans() {
        List<Loan> active = loans.findAll(Optional.of("ACTIVE"), Optional.empty());
        List<Loan> delinquent = loans.findAll(Optional.of("DELINQUENT"), Optional.empty());
        return java.util.stream.Stream.concat(active.stream(), delinquent.stream()).toList();
    }

    private boolean accrueLoan(Loan loan, LocalDate businessDate) {
        if (loan.disbursementDate() == null || !isAccruing(loan.state())) {
            return false;
        }
        List<ScheduleItem> items = scheduleItems(loan);
        Optional<DailyInterest> recognised =
                InterestAccrual.recognise(items, loan.disbursementDate(), businessDate);
        if (recognised.isEmpty()) {
            return false;
        }
        DailyInterest daily = recognised.get();
        boolean inserted =
                accruals.recordInterestAccrual(
                        loan.id(), businessDate, daily.instalmentNumber(), daily.amount());
        if (!inserted) {
            return false; // already accrued for this (loanId, businessDate): idempotent
        }
        publisher.interestAccrued(
                loan.id(),
                loan.loanNumber(),
                businessDate,
                daily.instalmentNumber(),
                daily.amount());
        return true;
    }

    private boolean isAccruing(LoanState state) {
        return state instanceof LoanState.Active || state instanceof LoanState.Delinquent;
    }

    private List<ScheduleItem> scheduleItems(Loan loan) {
        return schedules.findByLoanId(loan.id()).stream()
                .sorted(java.util.Comparator.comparingInt(LoanScheduleLine::instalmentNumber))
                .map(
                        l ->
                                new ScheduleItem(
                                        l.instalmentNumber(),
                                        l.dueDate(),
                                        l.scheduledTotal(),
                                        l.principal(),
                                        l.interest(),
                                        Money.zero(l.principal().currency())))
                .toList();
    }
}
