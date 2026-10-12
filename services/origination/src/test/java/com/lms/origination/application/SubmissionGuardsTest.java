package com.lms.origination.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class SubmissionGuardsTest {

    private static final Currency INR = Currency.getInstance("INR");

    private static ApplicantProfile profile(
            boolean kyc, boolean bank, boolean c1, boolean c2, boolean c3) {
        return new ApplicantProfile(
                kyc,
                bank,
                c1,
                c2,
                c3,
                LocalDate.of(1992, 1, 1),
                Money.of(5_000_000L, INR),
                "SALARIED",
                "hmac-abc",
                Money.zero(INR),
                0);
    }

    @Test
    void allowsWhenEverythingIsPresent() {
        SubmissionGuards.Result r = SubmissionGuards.check(profile(true, true, true, true, true));
        assertThat(r.allowed()).isTrue();
        assertThat(r.missing()).isEmpty();
    }

    @Test
    void reportsEachUnmetRequirement() {
        SubmissionGuards.Result r =
                SubmissionGuards.check(profile(false, false, false, false, false));
        assertThat(r.allowed()).isFalse();
        assertThat(r.missing())
                .containsExactly(
                        "KYC_NOT_VERIFIED",
                        "BANK_ACCOUNT_NOT_LINKED",
                        "CONSENT_DATA_PROCESSING_MISSING",
                        "CONSENT_CREDIT_PULL_MISSING",
                        "CONSENT_LOAN_AGREEMENT_MISSING");
    }

    @Test
    void reportsOnlyTheMissingConsent() {
        SubmissionGuards.Result r = SubmissionGuards.check(profile(true, true, true, false, true));
        assertThat(r.allowed()).isFalse();
        assertThat(r.missing()).containsExactly("CONSENT_CREDIT_PULL_MISSING");
    }
}
