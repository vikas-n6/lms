package com.lms.reporting.domain;

import com.lms.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;

/** A persisted daily portfolio snapshot (one row per business date). */
public record DailySnapshot(
        LocalDate businessDate,
        String currency,
        int totalLoans,
        int activeLoans,
        long outstandingPrincipalMinor,
        long disbursedMinor,
        long npaRatioBps,
        int dpdCurrent,
        int dpd1To30,
        int dpd31To60,
        int dpd61To90,
        int dpdNpa,
        Instant createdAt) {

    /** Build a snapshot row from computed portfolio metrics for a business date. */
    public static DailySnapshot from(
            LocalDate businessDate, PortfolioMetrics m, Instant createdAt) {
        Currency ccy = m.currency();
        return new DailySnapshot(
                businessDate,
                ccy.getCurrencyCode(),
                m.totalLoans(),
                m.activeLoans(),
                m.outstandingPrincipal().minorUnits(),
                m.totalDisbursed().minorUnits(),
                m.npaRatioBps(),
                m.dpdDistribution().getOrDefault(DpdBucketCategory.CURRENT, 0),
                m.dpdDistribution().getOrDefault(DpdBucketCategory.DPD_1_30, 0),
                m.dpdDistribution().getOrDefault(DpdBucketCategory.DPD_31_60, 0),
                m.dpdDistribution().getOrDefault(DpdBucketCategory.DPD_61_90, 0),
                m.dpdDistribution().getOrDefault(DpdBucketCategory.NPA, 0),
                createdAt);
    }

    public Money outstandingPrincipal() {
        return Money.of(outstandingPrincipalMinor, Currency.getInstance(currency));
    }
}
