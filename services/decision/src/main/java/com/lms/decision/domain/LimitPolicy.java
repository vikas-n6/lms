package com.lms.decision.domain;

import com.lms.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * Approved-limit bands by bureau score, always capped at {@code incomeMultiple} times monthly
 * income (docs/domain-rules.md "Eligibility rules"). Bands and the multiple come from the rule-set
 * JSON.
 */
public record LimitPolicy(List<Band> bands, int incomeMultiple) {

    /** Inclusive score band mapping to a maximum offered amount. */
    public record Band(int minScore, int maxScore, Money maxAmount) {
        public Band {
            Objects.requireNonNull(maxAmount, "maxAmount");
            if (minScore > maxScore) {
                throw new IllegalArgumentException("minScore must not exceed maxScore");
            }
        }

        boolean contains(int score) {
            return score >= minScore && score <= maxScore;
        }
    }

    public LimitPolicy {
        Objects.requireNonNull(bands, "bands");
        bands = List.copyOf(bands);
        if (incomeMultiple <= 0) {
            throw new IllegalArgumentException("incomeMultiple must be positive");
        }
    }

    /**
     * The offered limit for an approved applicant: the band's max, capped at {@code incomeMultiple}
     * x income and at the requested amount. Returns zero (in the requested currency) when no band
     * matches the score — which does not occur for an approvable score.
     */
    public Money offeredLimit(int bureauScore, Money monthlyIncome, Money requestedAmount) {
        Money incomeCap = monthlyIncome.times(incomeMultiple);
        for (Band band : bands) {
            if (band.contains(bureauScore)) {
                return band.maxAmount().min(incomeCap).min(requestedAmount);
            }
        }
        return Money.zero(requestedAmount.currency());
    }
}
