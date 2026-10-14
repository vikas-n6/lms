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
}
