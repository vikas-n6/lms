package com.lms.origination.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Submission preconditions (phase prompt S4): an application may be submitted only with verified
 * KYC, a linked bank account and all three consents. Pure; returns the list of unmet requirements
 * so the caller can reject with a precise reason rather than a bare boolean.
 */
public final class SubmissionGuards {

    private SubmissionGuards() {}

    public static Result check(ApplicantProfile profile) {
        List<String> missing = new ArrayList<>();
        if (!profile.kycVerified()) {
            missing.add("KYC_NOT_VERIFIED");
        }
        if (!profile.bankAccountLinked()) {
            missing.add("BANK_ACCOUNT_NOT_LINKED");
        }
        if (!profile.dataProcessingConsent()) {
            missing.add("CONSENT_DATA_PROCESSING_MISSING");
        }
        if (!profile.creditPullConsent()) {
            missing.add("CONSENT_CREDIT_PULL_MISSING");
        }
        if (!profile.loanAgreementConsent()) {
            missing.add("CONSENT_LOAN_AGREEMENT_MISSING");
        }
        return new Result(missing.isEmpty(), List.copyOf(missing));
    }

    /**
     * @param allowed true when every precondition is met
     * @param missing unmet requirement codes (empty when allowed)
     */
    public record Result(boolean allowed, List<String> missing) {}
}
