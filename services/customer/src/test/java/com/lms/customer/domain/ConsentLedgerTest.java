package com.lms.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class ConsentLedgerTest {

    private final Instant t = Instant.parse("2026-10-12T10:00:00Z");

    @Test
    void grantsAreAppendOnlyAndPreserveHistory() {
        ConsentLedger ledger =
                ConsentLedger.empty()
                        .grant(ConsentType.DATA_PROCESSING, t)
                        .grant(ConsentType.DATA_PROCESSING, t.plusSeconds(1)); // re-grant appends
        assertThat(ledger.entries()).hasSize(2);
        assertThat(ledger.hasGranted(ConsentType.DATA_PROCESSING)).isTrue();
    }

    @Test
    void hasAllRequiredOnlyWhenAllThreePresent() {
        ConsentLedger partial =
                ConsentLedger.empty()
                        .grant(ConsentType.DATA_PROCESSING, t)
                        .grant(ConsentType.CREDIT_PULL, t);
        assertThat(partial.hasAllRequired()).isFalse();

        ConsentLedger full = partial.grant(ConsentType.LOAN_AGREEMENT, t);
        assertThat(full.hasAllRequired()).isTrue();
        assertThat(full.hasAll(EnumSet.of(ConsentType.CREDIT_PULL))).isTrue();
    }

    @Test
    void emptyLedgerGrantsNothing() {
        assertThat(ConsentLedger.empty().hasGranted(ConsentType.LOAN_AGREEMENT)).isFalse();
    }
}
