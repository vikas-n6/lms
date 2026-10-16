package com.lms.disbursement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisbursementTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Instant T0 = Instant.parse("2026-10-20T04:30:00Z");

    private static Money inr(long minor) {
        return Money.of(minor, INR);
    }

    private Disbursement initiated() {
        return Disbursement.initiate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LN-2026-000001",
                inr(5_000_000L),
                inr(100_000L),
                inr(18_000L),
                inr(4_882_000L),
                T0);
    }

    @Test
    void initiateStartsInInitiatedWithNoAttempts() {
        Disbursement d = initiated();
        assertThat(d.status()).isEqualTo(DisbursementStatus.INITIATED);
        assertThat(d.attempts()).isZero();
        assertThat(d.railRef()).isNull();
        assertThat(d.net()).isEqualTo(inr(4_882_000L));
    }

    @Test
    void attemptStartedIncrementsAttempts() {
        Disbursement d = initiated().withAttemptStarted(T0).withAttemptStarted(T0);
        assertThat(d.attempts()).isEqualTo(2);
        assertThat(d.status()).isEqualTo(DisbursementStatus.INITIATED);
    }

    @Test
    void succeededRecordsUtrAndDate() {
        Disbursement d =
                initiated()
                        .withAttemptStarted(T0)
                        .succeeded("RAIL-1", "UTR-1", LocalDate.of(2026, 10, 20), T0);
        assertThat(d.status()).isEqualTo(DisbursementStatus.SUCCEEDED);
        assertThat(d.utr()).isEqualTo("UTR-1");
        assertThat(d.railRef()).isEqualTo("RAIL-1");
        assertThat(d.disbursementDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(d.failureReason()).isNull();
    }

    @Test
    void failedRecordsReasonAndIsTerminal() {
        Disbursement d = initiated().withAttemptStarted(T0).failed("RAIL_DECLINED", T0);
        assertThat(d.status()).isEqualTo(DisbursementStatus.FAILED);
        assertThat(d.status().isTerminal()).isTrue();
        assertThat(d.failureReason()).isEqualTo("RAIL_DECLINED");
    }

    @Test
    void manualReviewThenReinitiatedClearsFailureAndReturnsToInitiated() {
        Disbursement parked = initiated().withAttemptStarted(T0).toManualReview(T0);
        assertThat(parked.status()).isEqualTo(DisbursementStatus.MANUAL_REVIEW);
        assertThat(parked.status().isTerminal()).isFalse();

        Disbursement retried = parked.reinitiated(T0);
        assertThat(retried.status()).isEqualTo(DisbursementStatus.INITIATED);
        assertThat(retried.attempts()).isEqualTo(1); // preserved across the re-initiation
    }
}
