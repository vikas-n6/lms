package com.lms.reporting.domain;

import java.util.Collection;

/**
 * Application funnel (phase prompt task 1): submitted → approved → offered → accepted → disbursed,
 * with each step's conversion from the previous step in basis points. Counts are cumulative so the
 * funnel is monotonically non-increasing from submitted to disbursed.
 */
public record ApplicationFunnel(
        int submitted,
        int approved,
        int offered,
        int accepted,
        int disbursed,
        long approvedRateBps,
        long offeredRateBps,
        long acceptedRateBps,
        long disbursedRateBps) {

    public static ApplicationFunnel from(Collection<ApplicationFunnelRow> rows) {
        int submitted = 0;
        int approved = 0;
        int offered = 0;
        int accepted = 0;
        int disbursed = 0;
        for (ApplicationFunnelRow r : rows) {
            if (r.submitted()) {
                submitted++;
            }
            if (r.approved()) {
                approved++;
            }
            if (r.offered()) {
                offered++;
            }
            if (r.accepted()) {
                accepted++;
            }
            if (r.disbursed()) {
                disbursed++;
            }
        }
        return new ApplicationFunnel(
                submitted,
                approved,
                offered,
                accepted,
                disbursed,
                Ratios.basisPoints(approved, submitted),
                Ratios.basisPoints(offered, approved),
                Ratios.basisPoints(accepted, offered),
                Ratios.basisPoints(disbursed, accepted));
    }
}
