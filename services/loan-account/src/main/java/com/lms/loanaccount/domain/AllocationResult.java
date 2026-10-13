package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of running the allocation waterfall for one payment (docs/domain-rules.md "Repayment
 * allocation waterfall"): the per-instalment component lines, the component totals, and any advance
 * held in Customer suspense for the next instalment. The invariant {@code allocatedTotal + advance
 * == payment} always holds (asserted by a property test).
 *
 * @param allocations the per-instalment, per-component lines, in waterfall order
 * @param penalTotal total applied to penal across all instalments
 * @param interestTotal total applied to interest
 * @param principalTotal total applied to principal
 * @param advance excess held as advance (applied when the next instalment falls due)
 */
public record AllocationResult(
        List<Allocation> allocations,
        Money penalTotal,
        Money interestTotal,
        Money principalTotal,
        Money advance) {

    public AllocationResult {
        Objects.requireNonNull(allocations, "allocations");
        Objects.requireNonNull(penalTotal, "penalTotal");
        Objects.requireNonNull(interestTotal, "interestTotal");
        Objects.requireNonNull(principalTotal, "principalTotal");
        Objects.requireNonNull(advance, "advance");
        allocations = List.copyOf(allocations);
    }

    /** The amount applied to the loan (penal + interest + principal), excluding the advance. */
    public Money allocatedTotal() {
        return penalTotal.add(interestTotal).add(principalTotal);
    }
}
