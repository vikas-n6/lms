package com.lms.decision.domain;

import java.util.List;
import java.util.Objects;

/**
 * A versioned eligibility rule set (phase prompt S5): the ordered rules plus the limit policy.
 * Stored as JSON in {@code rule_sets} and loaded through a Redis cache keyed by {@link #version()}.
 * A loan decision records the version it was evaluated under so it can be replayed exactly.
 */
public record RuleSet(String version, List<Rule> rules, LimitPolicy limitPolicy) {

    public RuleSet {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(limitPolicy, "limitPolicy");
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("a rule set needs at least one rule");
        }
        rules = List.copyOf(rules);
    }
}
