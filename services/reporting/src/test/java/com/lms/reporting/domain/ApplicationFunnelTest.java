package com.lms.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationFunnelTest {

    private static ApplicationFunnelRow row(
            boolean sub, boolean app, boolean off, boolean acc, boolean dis) {
        return new ApplicationFunnelRow("a", sub, app, off, acc, dis, false, false);
    }

    @Test
    void countsCumulativeStagesAndConversionRates() {
        ApplicationFunnel f =
                ApplicationFunnel.from(
                        List.of(
                                row(true, true, true, true, true), // all the way to disbursed
                                row(true, true, true, true, false), // accepted, not disbursed
                                row(true, true, true, false, false), // offered, not accepted
                                row(true, true, false, false, false), // approved, not offered
                                row(true, false, false, false, false))); // submitted only

        assertThat(f.submitted()).isEqualTo(5);
        assertThat(f.approved()).isEqualTo(4);
        assertThat(f.offered()).isEqualTo(3);
        assertThat(f.accepted()).isEqualTo(2);
        assertThat(f.disbursed()).isEqualTo(1);

        assertThat(f.approvedRateBps()).isEqualTo(8_000L); // 4/5
        assertThat(f.offeredRateBps()).isEqualTo(7_500L); // 3/4
        assertThat(f.acceptedRateBps()).isEqualTo(6_666L); // 2/3 floored
        assertThat(f.disbursedRateBps()).isEqualTo(5_000L); // 1/2
    }

    @Test
    void countsAreMonotonicNonIncreasing() {
        ApplicationFunnel f =
                ApplicationFunnel.from(
                        List.of(
                                row(true, true, false, false, false),
                                row(true, false, false, false, false)));
        assertThat(f.submitted()).isGreaterThanOrEqualTo(f.approved());
        assertThat(f.approved()).isGreaterThanOrEqualTo(f.offered());
        assertThat(f.offered()).isGreaterThanOrEqualTo(f.accepted());
        assertThat(f.accepted()).isGreaterThanOrEqualTo(f.disbursed());
    }

    @Test
    void emptyFunnelIsZeroRates() {
        ApplicationFunnel f = ApplicationFunnel.from(List.of());
        assertThat(f.submitted()).isZero();
        assertThat(f.approvedRateBps()).isZero();
    }
}
