package com.lms.repayment.application;

import com.lms.repayment.application.port.out.DueInstalmentsPort;
import com.lms.repayment.application.port.out.DueInstalmentsPort.DueInstalment;
import com.lms.repayment.application.port.out.MandateClient;
import com.lms.repayment.application.port.out.MandateRepository;
import com.lms.repayment.domain.Mandate;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Auto-debit job (phase prompt task 3): for each loan with an instalment due on the business date
 * and an active mandate, requests a mandate debit. The terminal result arrives via the mandate
 * callback (which records the repayment or publishes {@code mandate.debit-failed}). The due loans
 * come from loan-account through the gateway, never the loan database.
 */
public class AutoDebitService {

    private final DueInstalmentsPort due;
    private final MandateRepository mandates;
    private final MandateClient client;
    private final String callbackUrl;

    public AutoDebitService(
            DueInstalmentsPort due,
            MandateRepository mandates,
            MandateClient client,
            String callbackUrl) {
        this.due = Objects.requireNonNull(due);
        this.mandates = Objects.requireNonNull(mandates);
        this.client = Objects.requireNonNull(client);
        this.callbackUrl = Objects.requireNonNull(callbackUrl);
    }

    /** Request debits for the due loans with an active mandate; returns the number requested. */
    public int run(LocalDate businessDate) {
        int requested = 0;
        for (DueInstalment instalment : due.dueOn(businessDate)) {
            Optional<Mandate> mandate = mandates.findActiveByLoanId(instalment.loanId());
            if (mandate.isEmpty()) {
                continue;
            }
            String idempotencyKey = "debit-" + instalment.loanId() + "-" + instalment.dueDate();
            client.debit(
                    mandate.get().providerMandateId(),
                    instalment.loanId(),
                    instalment.amount(),
                    idempotencyKey,
                    callbackUrl);
            requested++;
        }
        return requested;
    }
}
