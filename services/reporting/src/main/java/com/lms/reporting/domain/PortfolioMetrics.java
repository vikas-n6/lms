package com.lms.reporting.domain;

import com.lms.money.Money;
import java.util.Collection;
import java.util.Currency;
import java.util.EnumMap;
import java.util.Map;

/**
 * Portfolio snapshot (phase prompt task 1): active loan count, outstanding
 * principal/interest/penal, total disbursed, the DPD distribution and the NPA ratio. The NPA ratio
 * is NPA outstanding principal over total outstanding principal, in basis points (documented
 * simplification — a gross-NPA-to-gross-advances proxy using the live outstanding, not a 90-day
 * lagged average).
 */
public record PortfolioMetrics(
        Currency currency,
        int totalLoans,
        int activeLoans,
        Money outstandingPrincipal,
        Money outstandingInterest,
        Money outstandingPenal,
        Money totalDisbursed,
        Map<DpdBucketCategory, Integer> dpdDistribution,
        long npaRatioBps) {

    public static PortfolioMetrics from(Collection<LoanSnapshotRow> loans, Currency currency) {
        Money principal = Money.zero(currency);
        Money interest = Money.zero(currency);
        Money penal = Money.zero(currency);
        Money disbursed = Money.zero(currency);
        Money npaPrincipal = Money.zero(currency);
        int active = 0;
        Map<DpdBucketCategory, Integer> distribution = new EnumMap<>(DpdBucketCategory.class);
        for (DpdBucketCategory b : DpdBucketCategory.values()) {
            distribution.put(b, 0);
        }
        for (LoanSnapshotRow loan : loans) {
            principal = principal.add(loan.principalOutstanding());
            interest = interest.add(loan.interestOutstanding());
            penal = penal.add(loan.penalOutstanding());
            if (loan.disbursedAmount() != null) {
                disbursed = disbursed.add(loan.disbursedAmount());
            }
            if (loan.isActive()) {
                active++;
            }
            distribution.merge(loan.bucket(), 1, Integer::sum);
            if (loan.bucket().isNonPerforming()) {
                npaPrincipal = npaPrincipal.add(loan.principalOutstanding());
            }
        }
        long npaRatioBps = Ratios.basisPoints(npaPrincipal, principal);
        return new PortfolioMetrics(
                currency,
                loans.size(),
                active,
                principal,
                interest,
                penal,
                disbursed,
                distribution,
                npaRatioBps);
    }
}
