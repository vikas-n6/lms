package com.lms.origination.domain;

/**
 * The typed result of applying an {@link ApplicationTrigger} to an {@link ApplicationStatus}:
 * either a legal move to a new state, or an illegal transition that is rejected and audited, never
 * ignored (docs/domain-rules.md state-machine discipline).
 */
public sealed interface TransitionResult permits TransitionResult.Moved, TransitionResult.Illegal {

    record Moved(ApplicationStatus from, ApplicationTrigger trigger, ApplicationStatus to)
            implements TransitionResult {}

    record Illegal(ApplicationStatus from, ApplicationTrigger trigger)
            implements TransitionResult {}

    default boolean isLegal() {
        return this instanceof Moved;
    }
}
