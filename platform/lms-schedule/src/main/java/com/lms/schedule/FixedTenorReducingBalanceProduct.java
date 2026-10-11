package com.lms.schedule;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * The single v1 product: fixed-tenor, reducing-balance EMI micro-loan (docs/domain-rules.md "The
 * one product"). An immutable value whose fields are supplied from versioned configuration, never
 * literals in business code.
 *
 * <p>Uses a flexible constructor body (JEP: validate arguments before the record's implicit field
 * assignment) to reject inconsistent parameters.
 */
public record FixedTenorReducingBalanceProduct(
        String productId,
        int version,
        Currency currency,
        Money minPrincipal,
        Money maxPrincipal,
        Money principalStep,
        List<Integer> tenors,
        Rate annualInterestRate,
        Rate processingFeeRate,
        Rate gstRate,
        Rate penalAnnualRate,
        int graceDays,
        Rate foreclosureChargeRate)
        implements Product {

    public FixedTenorReducingBalanceProduct {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(minPrincipal, "minPrincipal");
        Objects.requireNonNull(maxPrincipal, "maxPrincipal");
        Objects.requireNonNull(principalStep, "principalStep");
        Objects.requireNonNull(annualInterestRate, "annualInterestRate");
        Objects.requireNonNull(processingFeeRate, "processingFeeRate");
        Objects.requireNonNull(gstRate, "gstRate");
        Objects.requireNonNull(penalAnnualRate, "penalAnnualRate");
        Objects.requireNonNull(foreclosureChargeRate, "foreclosureChargeRate");
        Objects.requireNonNull(tenors, "tenors");
        if (tenors.isEmpty()) {
            throw new IllegalArgumentException("at least one tenor is required");
        }
        tenors = List.copyOf(tenors);
        for (int t : tenors) {
            if (t <= 0) {
                throw new IllegalArgumentException("tenors must be positive: " + t);
            }
        }
        requireCurrency(minPrincipal, currency, "minPrincipal");
        requireCurrency(maxPrincipal, currency, "maxPrincipal");
        requireCurrency(principalStep, currency, "principalStep");
        if (minPrincipal.compareTo(maxPrincipal) > 0) {
            throw new IllegalArgumentException("minPrincipal must not exceed maxPrincipal");
        }
        if (!principalStep.isPositive()) {
            throw new IllegalArgumentException("principalStep must be positive");
        }
        if (graceDays < 0) {
            throw new IllegalArgumentException("graceDays must be non-negative");
        }
    }

    private static void requireCurrency(Money amount, Currency currency, String field) {
        if (!amount.currency().equals(currency)) {
            throw new IllegalArgumentException(field + " must be in " + currency.getCurrencyCode());
        }
    }
}
