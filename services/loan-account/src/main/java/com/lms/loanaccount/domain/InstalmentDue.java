package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.util.Objects;

/**
 * The amount still owed on one instalment, split by component, at allocation time (docs/domain-
 * rules.md "Repayment allocation waterfall"). Penal is what has accrued and is unpaid; interest and
 * principal are the scheduled portions not yet paid. All three are in the loan currency and
 * non-negative.
 *
 * @param instalmentNumber 1-based instalment this due belongs to
 * @param penalOutstanding unpaid penal accrued on this instalment
 * @param interestOutstanding unpaid scheduled interest
 * @param principalOutstanding unpaid scheduled principal
 */
public record InstalmentDue(
        int instalmentNumber,
        Money penalOutstanding,
        Money interestOutstanding,
        Money principalOutstanding) {

    public InstalmentDue {
        Objects.requireNonNull(penalOutstanding, "penalOutstanding");
        Objects.requireNonNull(interestOutstanding, "interestOutstanding");
        Objects.requireNonNull(principalOutstanding, "principalOutstanding");
        if (instalmentNumber <= 0) {
            throw new IllegalArgumentException("instalmentNumber must be positive");
        }
        if (penalOutstanding.isNegative()
                || interestOutstanding.isNegative()
                || principalOutstanding.isNegative()) {
            throw new IllegalArgumentException("outstanding components must be non-negative");
        }
    }

    /** The outstanding amount for a given component. */
    Money outstanding(Component component) {
        return switch (component) {
            case PENAL -> penalOutstanding;
            case INTEREST -> interestOutstanding;
            case PRINCIPAL -> principalOutstanding;
        };
    }
}
