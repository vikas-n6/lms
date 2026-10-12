package com.lms.origination.domain;

import com.lms.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The application aggregate (phase prompt S4). Immutable: each lifecycle operation returns a new
 * instance with the status advanced through {@link ApplicationStateMachine} (illegal transitions
 * throw, never applied silently) and the relevant data attached. Draft fields are captured by the
 * back-office wizard; KYC/bank status are read from the customer projection at submit time.
 */
public record Application(
        ApplicationId id,
        String customerId,
        String productId,
        Money requestedAmount,
        String phone,
        LocalDate dateOfBirth,
        Money declaredMonthlyIncome,
        String employmentType,
        boolean dataProcessingConsent,
        boolean creditPullConsent,
        boolean loanAgreementConsent,
        ApplicationStatus status,
        Optional<Offer> offer,
        Optional<AcceptedTerms> acceptedTerms,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public Application {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(requestedAmount, "requestedAmount");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(acceptedTerms, "acceptedTerms");
    }

    public static Application draft(
            ApplicationId id,
            String customerId,
            String productId,
            Money requestedAmount,
            String phone,
            LocalDate dateOfBirth,
            Money declaredMonthlyIncome,
            String employmentType,
            boolean dataProcessingConsent,
            boolean creditPullConsent,
            boolean loanAgreementConsent,
            Instant now) {
        return new Application(
                id,
                customerId,
                productId,
                requestedAmount,
                phone,
                dateOfBirth,
                declaredMonthlyIncome,
                employmentType,
                dataProcessingConsent,
                creditPullConsent,
                loanAgreementConsent,
                new ApplicationStatus.Draft(),
                Optional.empty(),
                Optional.empty(),
                now,
                now,
                0L);
    }

    private Application advance(
            ApplicationTrigger trigger, Offer newOffer, AcceptedTerms terms, Instant now) {
        TransitionResult result = ApplicationStateMachine.transition(status, trigger);
        if (!(result instanceof TransitionResult.Moved moved)) {
            throw new IllegalStateException(
                    "illegal transition " + status.code() + " via " + trigger);
        }
        return new Application(
                id,
                customerId,
                productId,
                requestedAmount,
                phone,
                dateOfBirth,
                declaredMonthlyIncome,
                employmentType,
                dataProcessingConsent,
                creditPullConsent,
                loanAgreementConsent,
                moved.to(),
                newOffer != null ? Optional.of(newOffer) : offer,
                terms != null ? Optional.of(terms) : acceptedTerms,
                createdAt,
                now,
                version);
    }

    public Application submit(Instant now) {
        return advance(new ApplicationTrigger.Submit(), null, null, now);
    }

    public Application recordDecision(Instant now) {
        return advance(new ApplicationTrigger.RecordDecision(), null, null, now);
    }

    public Application withOffer(Offer newOffer, Instant now) {
        return advance(new ApplicationTrigger.MakeOffer(), newOffer, null, now);
    }

    public Application reject(Instant now) {
        return advance(new ApplicationTrigger.Reject(), null, null, now);
    }

    public Application accept(AcceptedTerms terms, Instant now) {
        return advance(new ApplicationTrigger.Accept(), null, terms, now);
    }

    public Application expire(Instant now) {
        return advance(new ApplicationTrigger.Expire(), null, null, now);
    }

    public Application withdraw(Instant now) {
        return advance(new ApplicationTrigger.Withdraw(), null, null, now);
    }
}
