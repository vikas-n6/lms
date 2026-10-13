package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DpdBucketTest {

    @Test
    void bucketsByDaysPastDue() {
        assertThat(DpdBucket.of(0)).isEqualTo(DpdBucket.CURRENT);
        assertThat(DpdBucket.of(-5)).isEqualTo(DpdBucket.CURRENT);
        assertThat(DpdBucket.of(1)).isEqualTo(DpdBucket.DPD_1_30);
        assertThat(DpdBucket.of(30)).isEqualTo(DpdBucket.DPD_1_30);
        assertThat(DpdBucket.of(31)).isEqualTo(DpdBucket.DPD_31_60);
        assertThat(DpdBucket.of(60)).isEqualTo(DpdBucket.DPD_31_60);
        assertThat(DpdBucket.of(61)).isEqualTo(DpdBucket.DPD_61_90);
        assertThat(DpdBucket.of(90)).isEqualTo(DpdBucket.DPD_61_90);
        assertThat(DpdBucket.of(91)).isEqualTo(DpdBucket.NPA);
        assertThat(DpdBucket.of(365)).isEqualTo(DpdBucket.NPA);
    }

    @Test
    void daysPastDueFromOldestUnpaid() {
        LocalDate due = LocalDate.of(2026, 1, 10);
        assertThat(DpdBucket.daysPastDue(Optional.of(due), LocalDate.of(2026, 1, 25)))
                .isEqualTo(15);
        // Not yet due -> 0 (never negative).
        assertThat(DpdBucket.daysPastDue(Optional.of(due), LocalDate.of(2026, 1, 1))).isEqualTo(0);
        // Nothing overdue -> CURRENT.
        assertThat(DpdBucket.forOldestUnpaid(Optional.empty(), LocalDate.of(2026, 6, 1)))
                .isEqualTo(DpdBucket.CURRENT);
        assertThat(DpdBucket.forOldestUnpaid(Optional.of(due), LocalDate.of(2026, 3, 1)))
                .isEqualTo(DpdBucket.DPD_31_60);
    }
}
