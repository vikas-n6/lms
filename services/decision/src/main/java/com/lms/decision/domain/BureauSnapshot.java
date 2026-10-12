package com.lms.decision.domain;

import com.lms.money.Money;
import java.util.Optional;

/**
 * The bureau/bank data captured with a decision for the explainer and replay (docs/domain-rules.md:
 * every decision stores the bureau snapshot). When the bureau was unavailable, {@code available} is
 * false and the score is not meaningful.
 */
public record BureauSnapshot(
        boolean available,
        int score,
        boolean overdue,
        int activeAccounts,
        Optional<Money> bankEstimatedIncome) {}
