package com.lms.collections.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A versioned dunning plan (phase prompt task 4): the ordered steps and their DPD thresholds. The
 * plan is <b>configuration</b>, loaded from {@code lms.dunning.*} — never hard-coded in Java
 * (CLAUDE.md global do-not list). A case records the plan version it runs under. Pure: no I/O.
 */
public record DunningPlan(int version, List<DunningStep> steps) {

    public DunningPlan {
        Objects.requireNonNull(steps, "steps");
        if (version < 1) {
            throw new IllegalArgumentException("plan version must be >= 1");
        }
        steps = steps.stream().sorted(Comparator.comparingInt(DunningStep::dpdThreshold)).toList();
    }

    /**
     * The steps whose threshold has been reached at {@code dpd}, in threshold order. The dunning
     * workflow fires every such step that has not already fired for the case (phase prompt task 4:
     * "runs every step whose DPD threshold has been reached").
     */
    public List<DunningStep> stepsReachedBy(int dpd) {
        return steps.stream().filter(s -> s.dpdThreshold() <= dpd).toList();
    }

    /** The step with the given id, if any. */
    public java.util.Optional<DunningStep> step(String stepId) {
        return steps.stream().filter(s -> s.stepId().equals(stepId)).findFirst();
    }
}
