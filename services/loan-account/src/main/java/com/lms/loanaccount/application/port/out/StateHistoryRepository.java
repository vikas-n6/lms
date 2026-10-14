package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.domain.LoanState;
import java.util.UUID;

/**
 * Append-only store for loan state transitions (phase prompt task 2): every transition, and every
 * rejected illegal transition, is recorded with its cause and the event id that triggered it. Rows
 * are never updated or deleted (CLAUDE.md global do-not list).
 */
public interface StateHistoryRepository {

    /**
     * @param from previous state (null for the initial Created entry)
     * @param to new state (equal to {@code from} when recording a rejected transition)
     * @param cause human/eventType cause, e.g. {@code application.accepted}
     * @param eventId the event that triggered it (may be null for internal transitions)
     * @param legal whether the transition was applied (false = audited rejection)
     */
    void append(
            UUID loanId, LoanState from, LoanState to, String cause, UUID eventId, boolean legal);
}
