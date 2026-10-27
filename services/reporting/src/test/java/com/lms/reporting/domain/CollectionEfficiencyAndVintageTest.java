package com.lms.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionEfficiencyAndVintageTest {

    private static final java.util.Currency INR = java.util.Currency.getInstance("INR");

    private static Money inr(long minor) {
        return Money.of(minor, INR);
    }

    @Test
    void collectionEfficiencyIsCollectedOverBilledInBps() {
        CollectionEfficiency e = CollectionEfficiency.of(inr(10_000_00), inr(9_000_00));
        assertThat(e.efficiencyBps()).isEqualTo(9_000L); // 90%
    }

    @Test
    void collectionEfficiencyWithNothingBilledIsZero() {
        CollectionEfficiency e = CollectionEfficiency.of(Money.zero(INR), Money.zero(INR));
        assertThat(e.efficiencyBps()).isZero();
    }

    private static LoanSnapshotRow loan(String month, int dpd) {
        return new LoanSnapshotRow(
                UUID.randomUUID(),
                "cust",
                1,
                "Active",
                dpd,
                DpdBucketCategory.fromDpd(dpd),
                inr(100),
                inr(0),
                inr(0),
                inr(100),
                month);
    }

    @Test
    void vintageGroupsByMonthOrderedWith30PlusDelinquencyRate() {
        VintageCurve curve =
                VintageCurve.from(
                        List.of(
                                loan("2026-02", 0),
                                loan("2026-02", 45), // 30+ delinquent
                                loan("2026-01", 0),
                                loan("2026-01", 10), // under 30, not counted
                                loan("2026-01", 95), // 30+ delinquent
                                loan("2026-01", 0)));

        List<VintageCurve.VintagePoint> points = curve.points();
        assertThat(points).hasSize(2);
        // Ordered by month ascending.
        assertThat(points.get(0).disbursalMonth()).isEqualTo("2026-01");
        assertThat(points.get(1).disbursalMonth()).isEqualTo("2026-02");

        VintageCurve.VintagePoint jan = points.get(0);
        assertThat(jan.loans()).isEqualTo(4);
        assertThat(jan.delinquent()).isEqualTo(1); // only the 95-DPD one
        assertThat(jan.delinquencyBps()).isEqualTo(2_500L); // 1/4

        VintageCurve.VintagePoint feb = points.get(1);
        assertThat(feb.loans()).isEqualTo(2);
        assertThat(feb.delinquent()).isEqualTo(1);
        assertThat(feb.delinquencyBps()).isEqualTo(5_000L);
    }

    @Test
    void undisbursedLoansAreExcludedFromVintage() {
        LoanSnapshotRow undisbursed =
                new LoanSnapshotRow(
                        UUID.randomUUID(),
                        "c",
                        1,
                        "PendingDisbursal",
                        0,
                        DpdBucketCategory.CURRENT,
                        inr(0),
                        inr(0),
                        inr(0),
                        null,
                        null);
        VintageCurve curve = VintageCurve.from(List.of(undisbursed));
        assertThat(curve.points()).isEmpty();
    }
}
