package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.util.Objects;

/**
 * One line of a repayment allocation: an amount applied to a component of a specific instalment
 * (docs/domain-rules.md "Repayment allocation waterfall"). Stored as a row so the allocation is
 * deterministic and replayable.
 *
 * @param instalmentNumber the instalment the amount was applied to
 * @param component penal, interest or principal
 * @param amount the positive amount applied
 */
public record Allocation(int instalmentNumber, Component component, Money amount) {

    public Allocation {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(amount, "amount");
        if (instalmentNumber <= 0) {
            throw new IllegalArgumentException("instalmentNumber must be positive");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("allocation amount must be positive");
        }
    }
}
