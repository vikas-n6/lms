package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Example-based tests for the allocation waterfall (docs/domain-rules.md). */
class AllocationWaterfallTest {

    private static final Currency INR = Currency.getInstance("INR");

    private static Money inr(long minor) {
        return Money.of(minor, INR);
    }

    private static InstalmentDue due(int n, long penal, long interest, long principal) {
        return new InstalmentDue(n, inr(penal), inr(interest), inr(principal));
    }

    @Test
    void settlesPenalThenInterestThenPrincipalWithinAnInstalment() {
        InstalmentDue one = due(1, 500, 2_000, 10_000);
        AllocationResult result = AllocationWaterfall.allocate(List.of(one), inr(12_500));

        assertThat(result.allocations())
                .containsExactly(
                        new Allocation(1, Component.PENAL, inr(500)),
                        new Allocation(1, Component.INTEREST, inr(2_000)),
                        new Allocation(1, Component.PRINCIPAL, inr(10_000)));
        assertThat(result.advance()).isEqualTo(inr(0));
    }

    @Test
    void clearsOldestOverdueBeforeTheNext() {
        InstalmentDue first = due(1, 0, 2_000, 10_000);
        InstalmentDue second = due(2, 0, 1_800, 10_200);
        // Enough to clear the first instalment and part of the second's interest.
        AllocationResult result =
                AllocationWaterfall.allocate(List.of(first, second), inr(12_000 + 1_000));

        assertThat(result.allocations())
                .containsExactly(
                        new Allocation(1, Component.INTEREST, inr(2_000)),
                        new Allocation(1, Component.PRINCIPAL, inr(10_000)),
                        new Allocation(2, Component.INTEREST, inr(1_000)));
        assertThat(result.interestTotal()).isEqualTo(inr(3_000));
        assertThat(result.principalTotal()).isEqualTo(inr(10_000));
        assertThat(result.advance()).isEqualTo(inr(0));
    }

    @Test
    void excessBeyondAllDuesIsHeldAsAdvance() {
        InstalmentDue one = due(1, 0, 2_000, 10_000);
        AllocationResult result = AllocationWaterfall.allocate(List.of(one), inr(15_000));

        assertThat(result.allocatedTotal()).isEqualTo(inr(12_000));
        assertThat(result.advance()).isEqualTo(inr(3_000));
    }

    @Test
    void aShortPaymentIsFullyAllocatedWithNoAdvance() {
        InstalmentDue one = due(1, 500, 2_000, 10_000);
        AllocationResult result = AllocationWaterfall.allocate(List.of(one), inr(600));

        assertThat(result.allocations())
                .containsExactly(
                        new Allocation(1, Component.PENAL, inr(500)),
                        new Allocation(1, Component.INTEREST, inr(100)));
        assertThat(result.advance()).isEqualTo(inr(0));
        assertThat(result.allocatedTotal()).isEqualTo(inr(600));
    }

    @Test
    void aZeroPaymentAllocatesNothing() {
        InstalmentDue one = due(1, 500, 2_000, 10_000);
        AllocationResult result = AllocationWaterfall.allocate(List.of(one), inr(0));

        assertThat(result.allocations()).isEmpty();
        assertThat(result.advance()).isEqualTo(inr(0));
        assertThat(result.allocatedTotal()).isEqualTo(inr(0));
    }
}
