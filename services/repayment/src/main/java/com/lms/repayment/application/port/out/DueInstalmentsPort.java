package com.lms.repayment.application.port.out;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads which loans have an instalment due on a business date, via the gateway (gateway-mediated
 * REST, never the loan database — docs/architecture.md "No cross-service database access"). Used by
 * the auto-debit job.
 */
public interface DueInstalmentsPort {

    List<DueInstalment> dueOn(LocalDate businessDate);

    record DueInstalment(UUID loanId, LocalDate dueDate, Money amount) {}
}
