package com.lms.origination.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

/**
 * Builds the {@link ApplicantSnapshotData} captured at submit. Age is computed from the date of
 * birth against the business date from the injected {@link Clock} (CLAUDE.md: business dates use
 * {@code LocalDate} in Asia/Kolkata; the clock carries that zone). The date of birth itself is not
 * published — only {@code ageYears}.
 */
public final class ApplicantSnapshotFactory {

    private final Clock clock;

    public ApplicantSnapshotFactory(Clock clock) {
        this.clock = clock;
    }

    public ApplicantSnapshotData snapshot(ApplicantProfile profile) {
        int ageYears = Period.between(profile.dateOfBirth(), LocalDate.now(clock)).getYears();
        return new ApplicantSnapshotData(
                ageYears,
                profile.kycVerified() ? "VERIFIED" : "NOT_VERIFIED",
                profile.bankAccountLinked(),
                profile.declaredMonthlyIncome(),
                profile.employmentType(),
                profile.existingMonthlyEmi(),
                profile.activeLoanCount(),
                profile.phoneBlindIndex(),
                profile.dataProcessingConsent(),
                profile.creditPullConsent(),
                profile.loanAgreementConsent(),
                profile.phone());
    }
}
