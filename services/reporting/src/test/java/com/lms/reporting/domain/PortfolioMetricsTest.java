package com.lms.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioMetricsTest {

    private static final Currency INR = Currency.getInstance("INR");

    private static Money inr(long minor) {
        return Money.of(minor, INR);
    }

    private static LoanSnapshotRow loan(
            String state, int dpd, long principal, long interest, long penal, long disbursed) {
        return new LoanSnapshotRow(
                UUID.randomUUID(),
                "cust",
                1,
                state,
                dpd,
                DpdBucketCategory.fromDpd(dpd),
                inr(principal),
                inr(interest),
                inr(penal),
                disbursed < 0 ? null : inr(disbursed),
                disbursed < 0 ? null : "2026-01");
    }

    @Test
    void sumsBalancesAndCountsActive() {
        PortfolioMetrics m =
                PortfolioMetrics.from(
                        List.of(
                                loan("Active", 0, 10_000_00, 500_00, 0, 10_000_00),
                                loan("Delinquent", 45, 20_000_00, 800_00, 120_00, 20_000_00),
                                loan("Closed", 0, 0, 0, 0, 5_000_00)),
                        INR);

        assertThat(m.totalLoans()).isEqualTo(3);
        assertThat(m.activeLoans()).isEqualTo(2); // Active + Delinquent, not Closed
        assertThat(m.outstandingPrincipal()).isEqualTo(inr(30_000_00));
        assertThat(m.outstandingInterest()).isEqualTo(inr(1_300_00));
        assertThat(m.outstandingPenal()).isEqualTo(inr(120_00));
        assertThat(m.totalDisbursed()).isEqualTo(inr(35_000_00));
    }

    @Test
    void dpdDistributionCountsEachBucket() {
        PortfolioMetrics m =
                PortfolioMetrics.from(
                        List.of(
                                loan("Active", 0, 100, 0, 0, 100),
                                loan("Delinquent", 10, 100, 0, 0, 100),
                                loan("Delinquent", 45, 100, 0, 0, 100),
                                loan("Delinquent", 75, 100, 0, 0, 100),
                                loan("Delinquent", 200, 100, 0, 0, 100)),
                        INR);

        assertThat(m.dpdDistribution())
                .containsEntry(DpdBucketCategory.CURRENT, 1)
                .containsEntry(DpdBucketCategory.DPD_1_30, 1)
                .containsEntry(DpdBucketCategory.DPD_31_60, 1)
                .containsEntry(DpdBucketCategory.DPD_61_90, 1)
                .containsEntry(DpdBucketCategory.NPA, 1);
    }

    @Test
    void npaRatioIsNpaPrincipalOverTotalPrincipalInBps() {
        // NPA loan carries 25,000.00 principal; total principal is 100,000.00 -> 25% -> 2500 bps.
        PortfolioMetrics m =
                PortfolioMetrics.from(
                        List.of(
                                loan("Active", 0, 75_000_00, 0, 0, 75_000_00),
                                loan("Delinquent", 120, 25_000_00, 0, 0, 25_000_00)),
                        INR);
        assertThat(m.npaRatioBps()).isEqualTo(2_500L);
    }

    @Test
    void emptyPortfolioIsAllZeroNotAnError() {
        PortfolioMetrics m = PortfolioMetrics.from(List.of(), INR);
        assertThat(m.totalLoans()).isZero();
        assertThat(m.outstandingPrincipal()).isEqualTo(Money.zero(INR));
        assertThat(m.npaRatioBps()).isZero();
        assertThat(m.dpdDistribution()).containsEntry(DpdBucketCategory.CURRENT, 0);
    }
}
