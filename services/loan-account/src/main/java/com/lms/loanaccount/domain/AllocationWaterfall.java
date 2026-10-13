package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * The repayment allocation waterfall (docs/domain-rules.md "Repayment allocation waterfall"), as a
 * pure function. The caller supplies the instalments to settle in priority order — oldest overdue
 * first, then the currently due instalment — and within each instalment the payment is applied
 * penal → interest → principal. Any remainder beyond all supplied dues is held as advance in
 * Customer suspense and applied when the next instalment falls due.
 *
 * <p>Deterministic: the same inputs always produce the same result, and {@code allocatedTotal +
 * advance == payment} always holds. Partial prepayment is not supported (v1): excess simply becomes
 * advance (docs/domain-rules.md "The one product").
 */
public final class AllocationWaterfall {

    private AllocationWaterfall() {}

    public static AllocationResult allocate(List<InstalmentDue> dues, Money payment) {
        Objects.requireNonNull(dues, "dues");
        Objects.requireNonNull(payment, "payment");
        if (payment.isNegative()) {
            throw new IllegalArgumentException("payment must be non-negative");
        }
        Currency ccy = payment.currency();
        requireSameCurrency(dues, ccy);

        Money remaining = payment;
        Money penalTotal = Money.zero(ccy);
        Money interestTotal = Money.zero(ccy);
        Money principalTotal = Money.zero(ccy);
        List<Allocation> allocations = new ArrayList<>();

        for (InstalmentDue due : dues) {
            for (Component component : Component.values()) { // PENAL, INTEREST, PRINCIPAL
                Money apply = remaining.min(due.outstanding(component));
                if (!apply.isPositive()) {
                    continue;
                }
                allocations.add(new Allocation(due.instalmentNumber(), component, apply));
                remaining = remaining.subtract(apply);
                switch (component) {
                    case PENAL -> penalTotal = penalTotal.add(apply);
                    case INTEREST -> interestTotal = interestTotal.add(apply);
                    case PRINCIPAL -> principalTotal = principalTotal.add(apply);
                }
            }
        }

        return new AllocationResult(
                allocations, penalTotal, interestTotal, principalTotal, remaining);
    }

    private static void requireSameCurrency(List<InstalmentDue> dues, Currency ccy) {
        for (InstalmentDue due : dues) {
            if (!due.penalOutstanding().currency().equals(ccy)
                    || !due.interestOutstanding().currency().equals(ccy)
                    || !due.principalOutstanding().currency().equals(ccy)) {
                throw new IllegalArgumentException(
                        "instalment due currency does not match the payment currency");
            }
        }
    }
}
