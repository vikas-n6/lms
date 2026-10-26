package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.AccrualRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.domain.Delinquency;
import com.lms.loanaccount.domain.DelinquencySnapshot;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
import com.lms.loanaccount.domain.PenalAccrual;
import com.lms.money.Money;
import com.lms.schedule.Product;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The daily penal-accrual job (phase prompt task 1, docs/domain-rules.md "Interest and penal
 * accrual"): after the grace period, penal interest accrues each day on the overdue principal plus
 * overdue interest at the product's penal APR, simple, actual/365. Idempotent per {@code (loanId,
 * businessDate)} via the {@code accruals} unique key. The day's penal is attributed to the oldest
 * past-grace instalment (consistent with the oldest-first waterfall) and {@code loan.penal-accrued}
 * is published. The job takes the business date as a parameter, never the system clock.
 */
public class PenalAccrualService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final AccrualRepository accruals;
    private final ProductCatalog products;
    private final LoanEventPublisher publisher;

    public PenalAccrualService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AccrualRepository accruals,
            ProductCatalog products,
            LoanEventPublisher publisher) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.accruals = Objects.requireNonNull(accruals);
        this.products = Objects.requireNonNull(products);
        this.publisher = Objects.requireNonNull(publisher);
    }

    /** Accrue penal for all accruing loans on {@code businessDate}. Returns the number recorded. */
    public int runFor(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate");
        int recorded = 0;
        for (Loan loan : accruingLoans()) {
            if (accrue(loan, businessDate)) {
                recorded++;
            }
        }
        return recorded;
    }

    private boolean accrue(Loan loan, LocalDate businessDate) {
        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        if (lines.isEmpty()) {
            return false;
        }
        Product product = products.byVersion(loan.productId(), loan.productVersion());
        DelinquencySnapshot snapshot =
                Delinquency.assess(
                        lines, loan.principal().currency(), businessDate, product.graceDays());
        if (!snapshot.penalBase().isPositive() || snapshot.oldestOverdueInstalment().isEmpty()) {
            return false; // nothing past the grace window yet
        }
        Money penal = PenalAccrual.forDay(snapshot.penalBase(), product.penalAnnualRate());
        if (penal.isZero()) {
            return false;
        }
        int oldest = snapshot.oldestOverdueInstalment().get();
        boolean inserted = accruals.recordPenalAccrual(loan.id(), businessDate, oldest, penal);
        if (!inserted) {
            return false; // already accrued for this (loanId, businessDate): idempotent
        }
        applyToOldestOverdueLine(lines, oldest, penal);
        publisher.penalAccrued(
                loan.id(), loan.loanNumber(), businessDate, penal, snapshot.penalBase());
        return true;
    }

    private void applyToOldestOverdueLine(List<LoanScheduleLine> lines, int oldest, Money penal) {
        lines.stream()
                .filter(l -> l.instalmentNumber() == oldest)
                .findFirst()
                .ifPresent(line -> schedules.saveAll(List.of(line.accruePenal(penal))));
    }

    private List<Loan> accruingLoans() {
        Stream<Loan> active = loans.findAll(Optional.of("ACTIVE"), Optional.empty()).stream();
        Stream<Loan> delinquent =
                loans.findAll(Optional.of("DELINQUENT"), Optional.empty()).stream();
        return Stream.concat(active, delinquent)
                .filter(l -> l.disbursementDate() != null && isAccruing(l.state()))
                .sorted(Comparator.comparing(Loan::loanNumber))
                .toList();
    }

    private boolean isAccruing(LoanState state) {
        return state instanceof LoanState.Active || state instanceof LoanState.Delinquent;
    }
}
