package com.lms.decision.application.port.out;

import com.lms.decision.domain.RuleSet;
import java.util.Optional;

/**
 * Loads versioned rule sets (phase prompt S5). A loan decision records the version it used, so the
 * repository must return any historical version for replay. The production implementation reads the
 * {@code rule_sets} table through a Redis cache keyed by version (Docker session); the default
 * classpath-backed implementation serves the seeded rule sets in dev and tests.
 */
public interface RuleSetRepository {

    Optional<RuleSet> findByVersion(String version);

    /** The version evaluated for new applications. */
    String currentVersion();

    default RuleSet current() {
        return findByVersion(currentVersion())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "current rule set missing: " + currentVersion()));
    }
}
