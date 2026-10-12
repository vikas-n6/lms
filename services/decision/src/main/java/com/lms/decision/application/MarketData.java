package com.lms.decision.application;

import com.lms.money.Money;
import java.util.Optional;

/**
 * The result of the bureau + bank fan-out (phase prompt S5). When the bureau is unavailable after
 * retries, {@code bureauAvailable} is false and the decision refers with BUREAU_UNAVAILABLE rather
 * than hanging. The bank estimate is a recorded cross-check input.
 *
 * @param bureauAvailable whether the bureau pull succeeded
 * @param bureauScore bureau score (meaningful only when available)
 * @param bureauOverdue active overdue account reported
 * @param activeAccounts active credit accounts reported
 * @param bankEstimatedIncome statement-analysis income (empty when the bank call failed)
 */
public record MarketData(
        boolean bureauAvailable,
        int bureauScore,
        boolean bureauOverdue,
        int activeAccounts,
        Optional<Money> bankEstimatedIncome) {

    public static MarketData bureauUnavailable(Optional<Money> bankEstimatedIncome) {
        return new MarketData(false, 0, false, 0, bankEstimatedIncome);
    }
}
