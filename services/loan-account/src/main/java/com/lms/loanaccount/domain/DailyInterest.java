package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.util.Objects;

/**
 * The interest income recognised on one business day (docs/domain-rules.md "Interest and penal
 * accrual"), tagged with the instalment period it belongs to.
 *
 * @param instalmentNumber the instalment whose period the day falls in
 * @param amount interest recognised that day (the last day of a period takes the remainder)
 */
public record DailyInterest(int instalmentNumber, Money amount) {

    public DailyInterest {
        Objects.requireNonNull(amount, "amount");
        if (instalmentNumber <= 0) {
            throw new IllegalArgumentException("instalmentNumber must be positive");
        }
    }
}
