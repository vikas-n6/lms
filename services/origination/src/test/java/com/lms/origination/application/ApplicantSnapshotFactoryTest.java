package com.lms.origination.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class ApplicantSnapshotFactoryTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // Business "today" = 2026-10-12 IST.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T04:30:00Z"), IST);
    private final ApplicantSnapshotFactory factory = new ApplicantSnapshotFactory(clock);

    private ApplicantProfile profile(LocalDate dob) {
        return new ApplicantProfile(
                true,
                true,
                true,
                true,
                true,
                dob,
                Money.of(5_000_000L, INR),
                "SALARIED",
                "hmac-abc",
                "9000000001",
                Money.zero(INR),
                0);
    }

    @Test
    void computesAgeFromDobAndMapsFields() {
        ApplicantSnapshotData s = factory.snapshot(profile(LocalDate.of(1992, 1, 1)));
        assertThat(s.ageYears()).isEqualTo(34);
        assertThat(s.kycStatus()).isEqualTo("VERIFIED");
        assertThat(s.employmentType()).isEqualTo("SALARIED");
        assertThat(s.phoneBlindIndex()).isEqualTo("hmac-abc");
    }

    @Test
    void ageRespectsABirthdayNotYetReachedThisYear() {
        // DOB 2005-12-31: on 2026-10-12 the person is still 20, not 21.
        assertThat(factory.snapshot(profile(LocalDate.of(2005, 12, 31))).ageYears()).isEqualTo(20);
    }
}
