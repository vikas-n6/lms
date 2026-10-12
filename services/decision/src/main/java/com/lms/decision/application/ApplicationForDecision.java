package com.lms.decision.application;

import com.lms.money.Money;
import java.util.Objects;

/**
 * The application facts a decision needs, mapped by the Kafka adapter from the self-contained
 * {@code ApplicationSubmitted} snapshot (so the decision service never calls customer/origination
 * over REST). Plain types only — no Avro in the application layer.
 *
 * @param applicationId the application being decided
 * @param customerId the applicant
 * @param subjectId stable id used for the bureau/bank pulls
 * @param phone persona-bearing phone for the bureau pull (not persisted by decision)
 * @param requestedAmount requested principal
 * @param ageYears age in whole years at submit
 * @param kycVerified KYC status was VERIFIED
 * @param declaredMonthlyIncome declared monthly income
 * @param existingMonthlyEmi existing EMIs with us
 * @param activeLoanCount active loans with us
 */
public record ApplicationForDecision(
        String applicationId,
        String customerId,
        String subjectId,
        String phone,
        Money requestedAmount,
        int ageYears,
        boolean kycVerified,
        Money declaredMonthlyIncome,
        Money existingMonthlyEmi,
        int activeLoanCount) {

    public ApplicationForDecision {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(requestedAmount, "requestedAmount");
        Objects.requireNonNull(declaredMonthlyIncome, "declaredMonthlyIncome");
        Objects.requireNonNull(existingMonthlyEmi, "existingMonthlyEmi");
    }
}
