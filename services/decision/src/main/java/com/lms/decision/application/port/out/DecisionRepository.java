package com.lms.decision.application.port.out;

import com.lms.decision.domain.Decision;
import com.lms.decision.domain.DecisionId;
import java.util.Optional;

/**
 * Persistence port for decisions (JDBC adapter writes {@code decisions} + {@code
 * bureau_snapshots}).
 */
public interface DecisionRepository {

    void save(Decision decision);

    Optional<Decision> findById(DecisionId id);

    /** The most recent decision for an application (used to anchor a manual review override). */
    Optional<Decision> findLatestByApplication(String applicationId);

    /**
     * The manual-review queue: the latest REFER decision per application that has not since been
     * overridden, newest first (phase prompt S5).
     */
    java.util.List<Decision> findReferQueue(int limit);
}
