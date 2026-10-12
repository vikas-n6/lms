package com.lms.origination.application;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The applicant facts origination holds on a draft (sourced from customer events), used to gate
 * submission and to build the snapshot that travels in {@code application.submitted} so decision is
 * self-contained (phase prompt S4).
 */
public record ApplicantProfile(
        boolean kycVerified,
        boolean bankAccountLinked,
        boolean dataProcessingConsent,
        boolean creditPullConsent,
        boolean loanAgreementConsent,
        LocalDate dateOfBirth,
        Money declaredMonthlyIncome,
        String employmentType,
        String phoneBlindIndex,
        String phone,
        Money existingMonthlyEmi,
        int activeLoanCount) {

    public ApplicantProfile {
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        Objects.requireNonNull(declaredMonthlyIncome, "declaredMonthlyIncome");
        Objects.requireNonNull(employmentType, "employmentType");
        Objects.requireNonNull(phoneBlindIndex, "phoneBlindIndex");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(existingMonthlyEmi, "existingMonthlyEmi");
    }
}
