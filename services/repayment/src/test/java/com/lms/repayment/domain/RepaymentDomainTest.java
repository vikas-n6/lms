package com.lms.repayment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepaymentDomainTest {

    private static final Instant T0 = Instant.parse("2026-11-13T06:00:00Z");
    private static final Money INR = Money.of(474_000L, Currency.getInstance("INR"));

    @Test
    void mandateRegistersActiveAndCancels() {
        Mandate mandate =
                Mandate.register(UUID.randomUUID(), UUID.randomUUID(), "cust-1", "MND-1", T0);
        assertThat(mandate.status()).isEqualTo(MandateStatus.ACTIVE);
        assertThat(mandate.status().isActive()).isTrue();

        Mandate cancelled = mandate.cancel(T0);
        assertThat(cancelled.status()).isEqualTo(MandateStatus.CANCELLED);
        assertThat(cancelled.status().isActive()).isFalse();
    }

    @Test
    void repaymentReceivedThenReversed() {
        Repayment received =
                Repayment.received(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        INR,
                        RepaymentChannel.MANDATE,
                        null,
                        null,
                        "RAIL-1",
                        T0);
        assertThat(received.status()).isEqualTo(RepaymentStatus.RECEIVED);
        assertThat(received.isForeclosure()).isFalse();

        Repayment reversed = received.reversed(T0);
        assertThat(reversed.status()).isEqualTo(RepaymentStatus.REVERSED);
    }

    @Test
    void foreclosureRepaymentCarriesTheQuote() {
        Repayment fc =
                Repayment.received(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        INR,
                        RepaymentChannel.PAYMENT_LINK,
                        Repayment.FORECLOSURE,
                        "quote-9",
                        "PL-1",
                        T0);
        assertThat(fc.isForeclosure()).isTrue();
        assertThat(fc.foreclosureQuoteId()).isEqualTo("quote-9");
    }
}
