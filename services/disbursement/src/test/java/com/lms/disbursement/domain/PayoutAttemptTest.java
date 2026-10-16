package com.lms.disbursement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayoutAttemptTest {

    @Test
    void idempotencyKeyIsTiedToDisbursementAndAttempt() {
        UUID id = UUID.fromString("018f9a00-0000-7000-8000-000000000001");
        assertThat(PayoutAttempt.idempotencyKey(id, 2))
                .isEqualTo("disb-018f9a00-0000-7000-8000-000000000001-attempt-2");
    }

    @Test
    void finishedSetsTerminalStatusAndFinishInstant() {
        Instant start = Instant.parse("2026-10-20T04:30:00Z");
        Instant end = Instant.parse("2026-10-20T04:31:00Z");
        UUID disbId = UUID.randomUUID();
        PayoutAttempt pending =
                new PayoutAttempt(
                        UUID.randomUUID(),
                        disbId,
                        1,
                        PayoutAttempt.idempotencyKey(disbId, 1),
                        PayoutAttempt.Status.PENDING,
                        null,
                        start,
                        null);

        PayoutAttempt done = pending.finished(PayoutAttempt.Status.FAILED, "RAIL_DECLINED", end);
        assertThat(done.status()).isEqualTo(PayoutAttempt.Status.FAILED);
        assertThat(done.failureCode()).isEqualTo("RAIL_DECLINED");
        assertThat(done.finishedAt()).isEqualTo(end);
        assertThat(done.startedAt()).isEqualTo(start); // unchanged
    }
}
