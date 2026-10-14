package com.lms.loanaccount.domain;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The loan aggregate root (docs/architecture.md: loan-account owns all balances). Immutable; state
 * changes return a new instance. Holds the terms accepted from the offer and the product version it
 * was priced under, which never changes with later product versions (docs/domain-rules.md "The one
 * product"). Balances live in {@code schedule_items}/{@code accruals}; this row holds the contract
 * and lifecycle state.
 */
public record Loan(
        UUID id,
        String loanNumber,
        String applicationId,
        String customerId,
        String productId,
        int productVersion,
        Money principal,
        int tenorMonths,
        Money emi,
        Money fee,
        Money gst,
        Money netDisbursal,
        Rate annualInterestRate,
        LoanState state,
        LocalDate disbursementDate,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public Loan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loanNumber, "loanNumber");
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(emi, "emi");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(gst, "gst");
        Objects.requireNonNull(netDisbursal, "netDisbursal");
        Objects.requireNonNull(annualInterestRate, "annualInterestRate");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (tenorMonths <= 0) {
            throw new IllegalArgumentException("tenorMonths must be positive");
        }
    }

    /** A freshly created loan in {@link LoanState.Created}, before the provisional schedule. */
    public static Loan create(
            UUID id,
            String loanNumber,
            String applicationId,
            String customerId,
            String productId,
            int productVersion,
            Money principal,
            int tenorMonths,
            Money emi,
            Money fee,
            Money gst,
            Money netDisbursal,
            Rate annualInterestRate,
            Instant now) {
        return new Loan(
                id,
                loanNumber,
                applicationId,
                customerId,
                productId,
                productVersion,
                principal,
                tenorMonths,
                emi,
                fee,
                gst,
                netDisbursal,
                annualInterestRate,
                new LoanState.Created(),
                null,
                now,
                now,
                0L);
    }

    /** A copy moved to {@code newState} (the transition itself is validated by the caller). */
    public Loan withState(LoanState newState, Instant now) {
        return new Loan(
                id,
                loanNumber,
                applicationId,
                customerId,
                productId,
                productVersion,
                principal,
                tenorMonths,
                emi,
                fee,
                gst,
                netDisbursal,
                annualInterestRate,
                Objects.requireNonNull(newState, "newState"),
                disbursementDate,
                createdAt,
                now,
                version);
    }

    /** A copy that records the actual disbursement date and moves to Active. */
    public Loan activated(LocalDate disbursedOn, Instant now) {
        return new Loan(
                id,
                loanNumber,
                applicationId,
                customerId,
                productId,
                productVersion,
                principal,
                tenorMonths,
                emi,
                fee,
                gst,
                netDisbursal,
                annualInterestRate,
                new LoanState.Active(),
                Objects.requireNonNull(disbursedOn, "disbursedOn"),
                createdAt,
                now,
                version);
    }
}
