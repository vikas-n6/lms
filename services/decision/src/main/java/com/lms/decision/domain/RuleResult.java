package com.lms.decision.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of evaluating one {@link Rule} against {@link DecisionInputs}. A rule either passes,
 * fails (with a reason code), or refers (with a reason code). Stored per decision for the
 * explainer.
 */
public record RuleResult(String ruleId, Status status, ReasonCode reasonCode, String detail) {

    public enum Status {
        PASS,
        FAIL,
        REFER
    }

    public RuleResult {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
        if (status == Status.PASS && reasonCode != null) {
            throw new IllegalArgumentException("a passing rule carries no reason code");
        }
        if (status != Status.PASS && reasonCode == null) {
            throw new IllegalArgumentException("a non-passing rule requires a reason code");
        }
    }

    public static RuleResult pass(String ruleId, String detail) {
        return new RuleResult(ruleId, Status.PASS, null, detail);
    }

    public static RuleResult fail(String ruleId, ReasonCode reasonCode, String detail) {
        return new RuleResult(ruleId, Status.FAIL, reasonCode, detail);
    }

    public static RuleResult refer(String ruleId, ReasonCode reasonCode, String detail) {
        return new RuleResult(ruleId, Status.REFER, reasonCode, detail);
    }

    public Optional<ReasonCode> reason() {
        return Optional.ofNullable(reasonCode);
    }
}
