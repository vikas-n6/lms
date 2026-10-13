package com.lms.loanaccount.domain;

/**
 * The lifecycle state of a loan (docs/domain-rules.md "Loan state machine"): a sealed type with
 * exhaustive transitions (see {@link LoanStateMachine}). Each state is a distinct singleton value;
 * {@link #code()} is the stable string persisted in {@code loans.state} and {@code
 * loan_state_history.to_state}.
 */
public sealed interface LoanState
        permits LoanState.Created,
                LoanState.PendingDisbursal,
                LoanState.Active,
                LoanState.Delinquent,
                LoanState.Closed,
                LoanState.Foreclosed,
                LoanState.WrittenOff,
                LoanState.Cancelled {

    record Created() implements LoanState {}

    record PendingDisbursal() implements LoanState {}

    record Active() implements LoanState {}

    record Delinquent() implements LoanState {}

    record Closed() implements LoanState {}

    record Foreclosed() implements LoanState {}

    record WrittenOff() implements LoanState {}

    record Cancelled() implements LoanState {}

    /** The stable persisted code for this state (exhaustive switch, no default). */
    default String code() {
        return switch (this) {
            case Created ignored -> "CREATED";
            case PendingDisbursal ignored -> "PENDING_DISBURSAL";
            case Active ignored -> "ACTIVE";
            case Delinquent ignored -> "DELINQUENT";
            case Closed ignored -> "CLOSED";
            case Foreclosed ignored -> "FORECLOSED";
            case WrittenOff ignored -> "WRITTEN_OFF";
            case Cancelled ignored -> "CANCELLED";
        };
    }

    /** Terminal states accept no further transitions (docs/domain-rules.md). */
    default boolean isTerminal() {
        return switch (this) {
            case Closed ignored -> true;
            case Foreclosed ignored -> true;
            case WrittenOff ignored -> true;
            case Cancelled ignored -> true;
            case Created ignored -> false;
            case PendingDisbursal ignored -> false;
            case Active ignored -> false;
            case Delinquent ignored -> false;
        };
    }

    static LoanState fromCode(String code) {
        return switch (code) {
            case "CREATED" -> new Created();
            case "PENDING_DISBURSAL" -> new PendingDisbursal();
            case "ACTIVE" -> new Active();
            case "DELINQUENT" -> new Delinquent();
            case "CLOSED" -> new Closed();
            case "FORECLOSED" -> new Foreclosed();
            case "WRITTEN_OFF" -> new WrittenOff();
            case "CANCELLED" -> new Cancelled();
            default -> throw new IllegalArgumentException("unknown loan state: " + code);
        };
    }
}
