package com.lms.loanaccount.application;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Balances;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanBalances;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.ScheduleItemState;
import com.lms.money.Money;
import com.lms.schedule.Product;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model for the loan APIs (phase prompt task 2): list/filter, detail with derived balances,
 * schedule with per-instalment derived status, state-history timeline, and the foreclosure quote.
 * Balances and item status are derived, never stored (phase prompt "Do not").
 */
public class LoanQueryService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final StateHistoryRepository history;
    private final ProductCatalog products;
    private final BusinessCalendar calendar;

    public LoanQueryService(
            LoanRepository loans,
            ScheduleRepository schedules,
            StateHistoryRepository history,
            ProductCatalog products,
            BusinessCalendar calendar) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.history = Objects.requireNonNull(history);
        this.products = Objects.requireNonNull(products);
        this.calendar = Objects.requireNonNull(calendar);
    }

    public List<Loan> list(Optional<String> stateCode, Optional<String> customerId) {
        return loans.findAll(stateCode, customerId);
    }

    public Optional<LoanDetail> detail(UUID loanId) {
        return loans.findById(loanId)
                .map(
                        loan -> {
                            LocalDate today = calendar.today();
                            Product product =
                                    products.byVersion(loan.productId(), loan.productVersion());
                            List<LoanScheduleLine> lines = schedules.findByLoanId(loanId);
                            LoanBalances balances =
                                    Balances.of(lines, loan.principal().currency(), today);
                            List<ScheduleLineView> schedule =
                                    lines.stream()
                                            .map(l -> toView(l, today, product.graceDays()))
                                            .toList();
                            return new LoanDetail(
                                    loan, balances, schedule, history.findByLoanId(loanId));
                        });
    }

    public List<ScheduleLineView> schedule(UUID loanId) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        Product product = products.byVersion(loan.productId(), loan.productVersion());
        LocalDate today = calendar.today();
        return schedules.findByLoanId(loanId).stream()
                .map(l -> toView(l, today, product.graceDays()))
                .toList();
    }

    public Money foreclosureQuote(UUID loanId, LocalDate quoteDate) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        if (loan.disbursementDate() == null) {
            throw new IllegalStateException("loan is not yet disbursed: " + loanId);
        }
        Product product = products.byVersion(loan.productId(), loan.productVersion());
        return Balances.foreclosureQuote(
                schedules.findByLoanId(loanId),
                loan.principal().currency(),
                loan.annualInterestRate(),
                product.foreclosureChargeRate(),
                loan.disbursementDate(),
                quoteDate);
    }

    private ScheduleLineView toView(LoanScheduleLine line, LocalDate today, int graceDays) {
        ScheduleItemState status =
                ScheduleItemState.of(
                        line.scheduledTotal(), line.paidTotal(), line.dueDate(), today, graceDays);
        return new ScheduleLineView(
                line.instalmentNumber(),
                line.dueDate(),
                line.principal(),
                line.interest(),
                line.paidPrincipal(),
                line.paidInterest(),
                line.penalAccrued(),
                line.penalPaid(),
                line.provisional(),
                status.name());
    }

    /** A loan's full detail for the UI. */
    public record LoanDetail(
            Loan loan,
            LoanBalances balances,
            List<ScheduleLineView> schedule,
            List<StateHistoryRepository.Entry> history) {}

    /**
     * One schedule instalment with its derived status (UPCOMING/DUE/PARTIALLY_PAID/PAID/OVERDUE).
     */
    public record ScheduleLineView(
            int instalmentNumber,
            LocalDate dueDate,
            Money principal,
            Money interest,
            Money paidPrincipal,
            Money paidInterest,
            Money penalAccrued,
            Money penalPaid,
            boolean provisional,
            String status) {}
}
