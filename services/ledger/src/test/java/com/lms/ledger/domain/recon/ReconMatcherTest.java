package com.lms.ledger.domain.recon;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The reconciliation matcher (phase prompt task 6 acceptance tests): a clean day is all MATCHED,
 * and with drift injected each of the four discrepancy types is detected exactly.
 */
class ReconMatcherTest {

    private static final Currency INR = Currency.getInstance("INR");

    private static ReconLine line(String ref, long minor) {
        return new ReconLine(ref, Money.of(minor, INR));
    }

    @Test
    void cleanDayIsAllMatched() {
        List<ReconLine> external = List.of(line("UTR-1", 4_882_000), line("UTR-2", 474_000));
        List<ReconLine> ledger = List.of(line("UTR-1", 4_882_000), line("UTR-2", 474_000));

        ReconResult result = ReconMatcher.match(external, ledger);

        assertThat(result.isClean()).isTrue();
        assertThat(result.count(ReconClassification.MATCHED)).isEqualTo(2);
    }

    @Test
    void detectsEachDiscrepancyTypeExactly() {
        List<ReconLine> external =
                List.of(
                        line("UTR-OK", 4_882_000), // matched
                        line("UTR-ONLY-BANK", 100_000), // missing in ledger
                        line("UTR-DIFF", 474_000), // amount mismatch (ledger has 474_100)
                        line("UTR-DUP", 50_000), // duplicate (appears twice externally)
                        line("UTR-DUP", 50_000));
        List<ReconLine> ledger =
                List.of(
                        line("UTR-OK", 4_882_000),
                        line("UTR-ONLY-LEDGER", 200_000), // missing in bank
                        line("UTR-DIFF", 474_100),
                        line("UTR-DUP", 50_000));

        ReconResult result = ReconMatcher.match(external, ledger);

        assertThat(result.count(ReconClassification.MATCHED)).isEqualTo(1);
        assertThat(result.count(ReconClassification.MISSING_IN_LEDGER)).isEqualTo(1);
        assertThat(result.count(ReconClassification.MISSING_IN_BANK)).isEqualTo(1);
        assertThat(result.count(ReconClassification.AMOUNT_MISMATCH)).isEqualTo(1);
        assertThat(result.count(ReconClassification.DUPLICATE)).isEqualTo(1);
        assertThat(result.isClean()).isFalse();
        // One item per distinct reference (the duplicated reference collapses to one DUPLICATE
        // item).
        assertThat(result.items()).hasSize(5);
    }

    @Test
    void amountMismatchKeepsBothAmounts() {
        ReconResult result =
                ReconMatcher.match(
                        List.of(line("UTR-DIFF", 474_000)), List.of(line("UTR-DIFF", 474_100)));
        ReconItem item = result.items().get(0);
        assertThat(item.classification()).isEqualTo(ReconClassification.AMOUNT_MISMATCH);
        assertThat(item.externalAmount()).contains(Money.of(474_000, INR));
        assertThat(item.ledgerAmount()).contains(Money.of(474_100, INR));
    }
}
