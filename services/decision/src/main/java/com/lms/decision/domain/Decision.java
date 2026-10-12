package com.lms.decision.domain;

import com.lms.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A persisted, explainable, replayable decision (docs/domain-rules.md "Eligibility rules": every
 * decision stores the inputs snapshot, the bureau snapshot, the rule set version, each rule's
 * result, the reason codes, the outcome and the offered limit). A manual review override is a new
 * Decision with {@code manualOverride=true} and a {@code reviewNote}.
 */
public record Decision(
        DecisionId id,
        String applicationId,
        String customerId,
        Money requestedAmount,
        Outcome outcome,
        List<ReasonCode> reasonCodes,
        List<RuleResult> ruleResults,
        DecisionInputs inputs,
        BureauSnapshot bureauSnapshot,
        Optional<Money> offeredLimit,
        String ruleSetVersion,
        boolean manualOverride,
        Optional<String> reviewNote,
        Instant decidedAt) {

    public Decision {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(bureauSnapshot, "bureauSnapshot");
        Objects.requireNonNull(offeredLimit, "offeredLimit");
        Objects.requireNonNull(reviewNote, "reviewNote");
        Objects.requireNonNull(decidedAt, "decidedAt");
        reasonCodes = List.copyOf(reasonCodes);
        ruleResults = List.copyOf(ruleResults);
        if (outcome == Outcome.APPROVE && offeredLimit.isEmpty()) {
            throw new IllegalArgumentException("an APPROVE decision must carry an offered limit");
        }
    }
}
