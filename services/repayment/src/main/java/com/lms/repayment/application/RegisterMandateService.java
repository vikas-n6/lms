package com.lms.repayment.application;

import com.lms.events.Uuid7;
import com.lms.repayment.application.port.out.MandateClient;
import com.lms.repayment.application.port.out.MandateRepository;
import com.lms.repayment.application.port.out.RepaymentEventPublisher;
import com.lms.repayment.domain.Mandate;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Registers an auto-debit mandate with the provider, persists it, and publishes mandate.registered.
 */
public class RegisterMandateService {

    private final MandateRepository mandates;
    private final MandateClient client;
    private final RepaymentEventPublisher publisher;
    private final Clock clock;
    private final String callbackUrl;

    public RegisterMandateService(
            MandateRepository mandates,
            MandateClient client,
            RepaymentEventPublisher publisher,
            Clock clock,
            String callbackUrl) {
        this.mandates = Objects.requireNonNull(mandates);
        this.client = Objects.requireNonNull(client);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
        this.callbackUrl = Objects.requireNonNull(callbackUrl);
    }

    public Mandate register(UUID loanId, String customerId) {
        String providerMandateId = client.register(loanId, customerId, callbackUrl);
        Mandate mandate =
                Mandate.register(
                        Uuid7.generate(clock),
                        loanId,
                        customerId,
                        providerMandateId,
                        clock.instant());
        mandates.save(mandate);
        publisher.mandateRegistered(mandate);
        return mandate;
    }
}
