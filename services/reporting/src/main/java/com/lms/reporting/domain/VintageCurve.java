package com.lms.reporting.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Vintage curves by disbursal month (phase prompt task 1): for each cohort of loans disbursed in a
 * calendar month, the share that has become 30+ DPD (bucket {@code DPD_31_60} or worse), in basis
 * points. A standard vintage/delinquency curve; cohorts are ordered by month so the trend reads
 * left to right. Loans not yet disbursed (no disbursal month) are excluded.
 */
public record VintageCurve(List<VintagePoint> points) {

    public record VintagePoint(
            String disbursalMonth, int loans, int delinquent, long delinquencyBps) {}

    private static boolean isThirtyPlus(DpdBucketCategory bucket) {
        return switch (bucket) {
            case CURRENT, DPD_1_30 -> false;
            case DPD_31_60, DPD_61_90, NPA -> true;
        };
    }

    public static VintageCurve from(Collection<LoanSnapshotRow> loans) {
        Map<String, int[]> byMonth = new TreeMap<>(); // month -> [total, delinquent]
        for (LoanSnapshotRow loan : loans) {
            if (loan.disbursalMonth() == null) {
                continue;
            }
            int[] counts = byMonth.computeIfAbsent(loan.disbursalMonth(), m -> new int[2]);
            counts[0]++;
            if (isThirtyPlus(loan.bucket())) {
                counts[1]++;
            }
        }
        List<VintagePoint> points = new ArrayList<>(byMonth.size());
        byMonth.forEach(
                (month, counts) ->
                        points.add(
                                new VintagePoint(
                                        month,
                                        counts[0],
                                        counts[1],
                                        Ratios.basisPoints(counts[1], counts[0]))));
        return new VintageCurve(points);
    }
}
