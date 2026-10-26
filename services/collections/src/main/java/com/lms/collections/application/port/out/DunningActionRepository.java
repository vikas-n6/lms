package com.lms.collections.application.port.out;

import com.lms.collections.domain.DunningAction;
import java.util.List;
import java.util.UUID;

/**
 * Append-only store of dunning actions (phase prompt task 4). A unique {@code (case_id, step_id)}
 * makes {@link #recordIfAbsent} fire each step at most once, so replays/duplicate ticks never
 * repeat.
 */
public interface DunningActionRepository {

    /**
     * Insert the action unless {@code (caseId, stepId)} already exists. Returns true if inserted.
     */
    boolean recordIfAbsent(DunningAction action);

    /** The action timeline for a case, oldest first. */
    List<DunningAction> findByCaseId(UUID caseId);
}
