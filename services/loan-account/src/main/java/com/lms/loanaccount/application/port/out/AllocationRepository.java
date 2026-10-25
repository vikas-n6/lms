package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.domain.Allocation;
import java.util.List;
import java.util.UUID;

/**
 * Append-only store for repayment allocation lines (docs/domain-rules.md "Repayment allocation
 * waterfall" rule 4): rows are deterministic and replayable. A reversal is a new set of rows, never
 * an edit.
 */
public interface AllocationRepository {

    void saveAll(UUID loanId, String repaymentId, List<Allocation> allocations);

    /** The original allocation lines recorded for a repayment (used to reverse it). */
    List<Allocation> findByRepaymentId(UUID loanId, String repaymentId);

    /**
     * Record reversing rows for a returned repayment — new rows, never an edit (the table is
     * append-only). {@code reversedRepaymentId} links each row back to the repayment being undone.
     */
    void saveReversal(
            UUID loanId,
            String reversalRepaymentId,
            String reversedRepaymentId,
            List<Allocation> allocations);
}
