package com.lms.schedule;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * A lending product's versioned parameters (docs/domain-rules.md "The one product"). Every value is
 * configuration, never a Java literal in business code; a loan records the product version it was
 * created under and never changes with later versions.
 */
public interface Product {

    String productId();

    /** Monotonic version of the parameter set. A loan pins the version it was priced under. */
    int version();

    Currency currency();

    Money minPrincipal();

    Money maxPrincipal();

    /** The step amount principals must be a multiple of (e.g. 500.00). */
    Money principalStep();

    /** Allowed tenors in whole months (e.g. 3, 6, 9, 12). */
    List<Integer> tenors();

    Rate annualInterestRate();

    Rate processingFeeRate();

    Rate gstRate();

    Rate penalAnnualRate();

    int graceDays();

    Rate foreclosureChargeRate();

    /** Nominal monthly rate = APR / 12 (docs/domain-rules.md "Schedule engine"). */
    default Rate monthlyInterestRate() {
        return annualInterestRate().dividedBy(12);
    }

    /** True when {@code principal} is within range and on a valid step, in the product currency. */
    default boolean supportsPrincipal(Money principal) {
        if (!principal.currency().equals(currency())) {
            return false;
        }
        if (principal.compareTo(minPrincipal()) < 0 || principal.compareTo(maxPrincipal()) > 0) {
            return false;
        }
        long step = principalStep().minorUnits();
        return step > 0 && (principal.minorUnits() - minPrincipal().minorUnits()) % step == 0;
    }

    /**
     * The largest supported principal not exceeding {@code amount}: {@code amount} capped at the
     * maximum, then floored to the nearest step at or above the minimum. Empty when {@code amount}
     * is below the minimum or in a different currency. Origination uses this to turn a decision's
     * approved limit (which need not sit on a step) into an offerable principal.
     */
    default Optional<Money> largestSupportedAtMost(Money amount) {
        if (!amount.currency().equals(currency()) || amount.compareTo(minPrincipal()) < 0) {
            return Optional.empty();
        }
        Money capped = amount.min(maxPrincipal());
        long step = principalStep().minorUnits();
        long steps = (capped.minorUnits() - minPrincipal().minorUnits()) / step; // floor, >= 0
        return Optional.of(minPrincipal().add(principalStep().times(steps)));
    }
}
