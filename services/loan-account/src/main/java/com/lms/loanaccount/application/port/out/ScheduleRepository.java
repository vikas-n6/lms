package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.domain.LoanScheduleLine;
import java.util.List;
import java.util.UUID;

/**
 * Persistence port for {@code schedule_items}. The provisional schedule is replaced by the real one
 * when disbursement succeeds (docs/domain-rules.md "Schedule engine" rule 6).
 */
public interface ScheduleRepository {

    List<LoanScheduleLine> findByLoanId(UUID loanId);

    /** Replace all schedule lines for a loan (used for provisional -> real on activation). */
    void replaceSchedule(UUID loanId, List<LoanScheduleLine> lines);

    /** Persist updated component amounts for the given lines (after an allocation or accrual). */
    void saveAll(List<LoanScheduleLine> lines);
}
