package com.lms.origination.domain;

/**
 * The lifecycle state of a loan application (phase prompt S4): a sealed type with exhaustive
 * transitions (see {@link ApplicationStateMachine}). Each state is a distinct singleton value;
 * {@link #code()} is the stable string persisted in {@code applications.status}.
 */
public sealed interface ApplicationStatus
        permits ApplicationStatus.Draft,
                ApplicationStatus.Submitted,
                ApplicationStatus.Decisioned,
                ApplicationStatus.Offered,
                ApplicationStatus.Accepted,
                ApplicationStatus.Rejected,
                ApplicationStatus.Expired,
                ApplicationStatus.Withdrawn {

    record Draft() implements ApplicationStatus {}

    record Submitted() implements ApplicationStatus {}

    record Decisioned() implements ApplicationStatus {}

    record Offered() implements ApplicationStatus {}

    record Accepted() implements ApplicationStatus {}

    record Rejected() implements ApplicationStatus {}

    record Expired() implements ApplicationStatus {}

    record Withdrawn() implements ApplicationStatus {}

    /** The stable persisted code for this state (exhaustive switch, no default). */
    default String code() {
        return switch (this) {
            case Draft ignored -> "DRAFT";
            case Submitted ignored -> "SUBMITTED";
            case Decisioned ignored -> "DECISIONED";
            case Offered ignored -> "OFFERED";
            case Accepted ignored -> "ACCEPTED";
            case Rejected ignored -> "REJECTED";
            case Expired ignored -> "EXPIRED";
            case Withdrawn ignored -> "WITHDRAWN";
        };
    }

    /** Terminal states accept no further transitions. */
    default boolean isTerminal() {
        return switch (this) {
            case Accepted ignored -> true;
            case Rejected ignored -> true;
            case Expired ignored -> true;
            case Withdrawn ignored -> true;
            case Draft ignored -> false;
            case Submitted ignored -> false;
            case Decisioned ignored -> false;
            case Offered ignored -> false;
        };
    }

    static ApplicationStatus fromCode(String code) {
        return switch (code) {
            case "DRAFT" -> new Draft();
            case "SUBMITTED" -> new Submitted();
            case "DECISIONED" -> new Decisioned();
            case "OFFERED" -> new Offered();
            case "ACCEPTED" -> new Accepted();
            case "REJECTED" -> new Rejected();
            case "EXPIRED" -> new Expired();
            case "WITHDRAWN" -> new Withdrawn();
            default -> throw new IllegalArgumentException("unknown application status: " + code);
        };
    }
}
