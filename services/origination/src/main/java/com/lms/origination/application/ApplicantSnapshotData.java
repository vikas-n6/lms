package com.lms.origination.application;

import com.lms.money.Money;

/**
 * The plain applicant snapshot captured at submit and carried in {@code application.submitted} (the
 * Kafka adapter maps this to the Avro record). It is self-contained so decision never calls
 * customer/origination synchronously. No plaintext PII: the phone travels only as a blind index;
 * the date of birth is reduced to {@code ageYears}.
 */
public record ApplicantSnapshotData(
        int ageYears,
        String kycStatus,
        boolean bankAccountLinked,
        Money declaredMonthlyIncome,
        String employmentType,
        Money existingMonthlyEmi,
        int activeLoanCount,
        String phoneBlindIndex,
        boolean dataProcessingConsent,
        boolean creditPullConsent,
        boolean loanAgreementConsent,
        String bureauSubjectPhone) {}
