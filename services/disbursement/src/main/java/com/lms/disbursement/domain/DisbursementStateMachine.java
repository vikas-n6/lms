package com.lms.disbursement.domain;

/**
 * Pure resolution of an incoming rail result against the current disbursement state. It encodes
 * only the idempotency/conflict rule (phase prompt: duplicate callbacks are ignored, a conflicting
 * terminal callback after a recorded one is ignored and counted). Whether an {@code APPLIED}
 * failure leads to another attempt or to a terminal {@code FAILED} is a {@link RetryPolicy}
 * decision made by the workflow, not here.
 */
public final class DisbursementStateMachine {

    private DisbursementStateMachine() {}

    /** Classify how {@code incoming} should be treated given the {@code current} state. */
    public static CallbackResolution resolve(DisbursementStatus current, RailStatus incoming) {
        return switch (current) {
            case INITIATED, MANUAL_REVIEW -> CallbackResolution.APPLIED;
            case SUCCEEDED ->
                    incoming == RailStatus.SUCCESS
                            ? CallbackResolution.DUPLICATE
                            : CallbackResolution.CONFLICT;
            case FAILED ->
                    incoming == RailStatus.FAILED
                            ? CallbackResolution.DUPLICATE
                            : CallbackResolution.CONFLICT;
        };
    }
}
