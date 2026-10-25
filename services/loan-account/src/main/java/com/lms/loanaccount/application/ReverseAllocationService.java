package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.AllocationRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.domain.Allocation;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.money.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@code repayment.reversed} (a returned debit): undoes a prior allocation with new
 * reversing records (never edits — docs/domain-rules.md "Repayment allocation waterfall" rule 5),
 * reopens the affected instalments, and publishes {@code loan.repayment-reversed}. Idempotent at
 * the listener via the inbox; a reversal with no original allocation on record is a no-op.
 */
public class ReverseAllocationService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final AllocationRepository allocations;
    private final LoanEventPublisher publisher;

    public ReverseAllocationService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AllocationRepository allocations,
            LoanEventPublisher publisher) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.allocations = Objects.requireNonNull(allocations);
        this.publisher = Objects.requireNonNull(publisher);
    }

    public void reverse(UUID loanId, String repaymentId, UUID sourceEventId) {
        Objects.requireNonNull(repaymentId, "repaymentId");
        Loan loan = loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));

        List<Allocation> original = allocations.findByRepaymentId(loanId, repaymentId);
        if (original.isEmpty()) {
            return; // nothing was allocated for this repayment; nothing to reverse
        }

        List<LoanScheduleLine> lines = new ArrayList<>(schedules.findByLoanId(loanId));
        lines.sort(Comparator.comparingInt(LoanScheduleLine::instalmentNumber));
        Currency currency = loan.principal().currency();

        Map<Integer, Money> penal = new LinkedHashMap<>();
        Map<Integer, Money> interest = new LinkedHashMap<>();
        Map<Integer, Money> principal = new LinkedHashMap<>();
        Money penalTotal = Money.zero(currency);
        Money interestTotal = Money.zero(currency);
        Money principalTotal = Money.zero(currency);
        for (Allocation a : original) {
            switch (a.component()) {
                case PENAL -> {
                    penal.merge(a.instalmentNumber(), a.amount(), Money::add);
                    penalTotal = penalTotal.add(a.amount());
                }
                case INTEREST -> {
                    interest.merge(a.instalmentNumber(), a.amount(), Money::add);
                    interestTotal = interestTotal.add(a.amount());
                }
                case PRINCIPAL -> {
                    principal.merge(a.instalmentNumber(), a.amount(), Money::add);
                    principalTotal = principalTotal.add(a.amount());
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            LoanScheduleLine line = lines.get(i);
            int n = line.instalmentNumber();
            Money zero = Money.zero(currency);
            Money p = penal.getOrDefault(n, zero);
            Money in = interest.getOrDefault(n, zero);
            Money pr = principal.getOrDefault(n, zero);
            if (!p.isZero() || !in.isZero() || !pr.isZero()) {
                lines.set(i, line.reversePayment(p, in, pr));
            }
        }
        schedules.saveAll(lines);
        allocations.saveReversal(loanId, repaymentId + "-rev", repaymentId, original);
        publisher.repaymentReversed(
                loanId,
                loan.loanNumber(),
                repaymentId,
                penalTotal,
                interestTotal,
                principalTotal,
                sourceEventId);
    }
}
