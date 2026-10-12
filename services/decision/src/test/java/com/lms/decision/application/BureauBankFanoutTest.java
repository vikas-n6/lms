package com.lms.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.decision.application.port.out.BureauClient;
import com.lms.money.Money;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Fan-out behaviour with fake clients. Runs with --enable-preview (lms-concurrency, ADR 0006). */
class BureauBankFanoutTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void bothProvidersSucceed() {
        BureauBankFanout fanout =
                new BureauBankFanout(
                        (id, phone) -> new BureauClient.BureauReport(760, 1, false),
                        id -> Money.of(5_000_000L, INR));

        MarketData data = fanout.fetch("subject-1", "9000000001");

        assertThat(data.bureauAvailable()).isTrue();
        assertThat(data.bureauScore()).isEqualTo(760);
        assertThat(data.bankEstimatedIncome()).contains(Money.of(5_000_000L, INR));
    }

    @Test
    void bureauFailureBecomesUnavailableWithoutAbortingBank() {
        BureauBankFanout fanout =
                new BureauBankFanout(
                        (id, phone) -> {
                            throw new RuntimeException("bureau 503");
                        },
                        id -> Money.of(4_000_000L, INR));

        MarketData data = fanout.fetch("subject-2", "9000000001");

        assertThat(data.bureauAvailable()).isFalse();
        assertThat(data.bankEstimatedIncome()).contains(Money.of(4_000_000L, INR));
    }

    @Test
    void bankFailureLeavesIncomeEmptyButBureauAvailable() {
        BureauBankFanout fanout =
                new BureauBankFanout(
                        (id, phone) -> new BureauClient.BureauReport(700, 0, false),
                        id -> {
                            throw new RuntimeException("bank down");
                        });

        MarketData data = fanout.fetch("subject-3", "9000000001");

        assertThat(data.bureauAvailable()).isTrue();
        assertThat(data.bankEstimatedIncome()).isEqualTo(Optional.empty());
    }
}
