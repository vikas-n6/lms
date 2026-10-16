package com.lms.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.events.EventMetadata;
import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.loan.LoanForeclosed;
import com.lms.contracts.loan.LoanRepaymentReversed;
import com.lms.contracts.repayment.MandateDebitFailed;
import com.lms.contracts.repayment.MandateRegistered;
import com.lms.contracts.repayment.RepaymentFailed;
import com.lms.contracts.repayment.RepaymentReceived;
import com.lms.contracts.repayment.RepaymentReversed;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

/**
 * Serde round-trips for the Phase 3 money-movement events (repayment failed/reversed, mandate
 * registered/debit-failed, loan repayment-reversed/foreclosed) plus the foreclosure additions to
 * {@code repayment.received}. Exercises nullable unions ({@code rePresentOn}, {@code purpose},
 * {@code foreclosureQuoteId}) and {@code date} logical types through the Schema-Registry serde with
 * no running registry (MockSchemaRegistryClient), so it runs on JDK 25 without Docker.
 */
class Phase3EventsSerdeTest {

    private static MoneyAmount inr(long minor) {
        return MoneyAmount.newBuilder().setMinorUnits(minor).setCurrency("INR").build();
    }

    private static EventMetadata meta(String type, String aggregateType, String aggregateId) {
        return new EventMetadata(
                "018f9a00-0000-7000-8000-000000000031",
                type,
                1,
                Instant.parse("2026-11-13T04:30:00.000Z"),
                aggregateType,
                aggregateId,
                "corr-31",
                null,
                "trace-p3",
                "repayment");
    }

    private static <T> void assertRoundTrips(String topic, T event) {
        SchemaRegistryClient registry = new MockSchemaRegistryClient();
        Serializer<Object> serializer = AvroEventSerde.serializer(registry, true);
        Deserializer<Object> deserializer = AvroEventSerde.deserializer(registry);

        byte[] bytes = serializer.serialize(topic, event);
        Object decoded = deserializer.deserialize(topic, bytes);

        assertThat(decoded).isEqualTo(event);
    }

    @Test
    void repaymentReceivedDefaultsChannelAndNullPurpose() {
        RepaymentReceived ordinary =
                RepaymentReceived.newBuilder()
                        .setMetadata(meta("repayment.received", "loan", "loan-1"))
                        .setRepaymentId("rep-1")
                        .setLoanId("loan-1")
                        .setAmount(inr(474_000L))
                        .setRailReference("RAIL-777")
                        .setReceivedAt(Instant.parse("2026-11-13T06:00:00.000Z"))
                        .build();

        // The new fields default without being set (BACKWARD-compatible addition).
        assertThat(ordinary.getChannel()).isEqualTo("MANDATE");
        assertThat(ordinary.getPurpose()).isNull();
        assertThat(ordinary.getForeclosureQuoteId()).isNull();
        assertRoundTrips("repayment.events", ordinary);
    }

    @Test
    void repaymentReceivedWithForeclosurePurpose() {
        RepaymentReceived foreclosure =
                RepaymentReceived.newBuilder()
                        .setMetadata(meta("repayment.received", "loan", "loan-1"))
                        .setRepaymentId("rep-fc")
                        .setLoanId("loan-1")
                        .setAmount(inr(4_900_000L))
                        .setRailReference("RAIL-FC")
                        .setChannel("PAYMENT_LINK")
                        .setPurpose("FORECLOSURE")
                        .setForeclosureQuoteId("quote-9")
                        .setReceivedAt(Instant.parse("2026-11-13T06:00:00.000Z"))
                        .build();
        assertRoundTrips("repayment.events", foreclosure);
    }

    @Test
    void repaymentFailedRoundTrips() {
        RepaymentFailed event =
                RepaymentFailed.newBuilder()
                        .setMetadata(meta("repayment.failed", "loan", "loan-1"))
                        .setRepaymentId("rep-2")
                        .setLoanId("loan-1")
                        .setAmount(inr(474_000L))
                        .setChannel("PAYMENT_LINK")
                        .setReason("LINK_EXPIRED")
                        .setFailedAt(Instant.parse("2026-11-13T07:00:00.000Z"))
                        .build();
        assertRoundTrips("repayment.events", event);
    }

    @Test
    void repaymentReversedRoundTrips() {
        RepaymentReversed event =
                RepaymentReversed.newBuilder()
                        .setMetadata(meta("repayment.reversed", "loan", "loan-1"))
                        .setRepaymentId("rep-1")
                        .setLoanId("loan-1")
                        .setAmount(inr(474_000L))
                        .setRailReference("RAIL-777")
                        .setReason("DEBIT_RETURNED")
                        .setReversedAt(Instant.parse("2026-11-15T07:00:00.000Z"))
                        .build();
        assertRoundTrips("repayment.events", event);
    }

    @Test
    void mandateRegisteredRoundTrips() {
        MandateRegistered event =
                MandateRegistered.newBuilder()
                        .setMetadata(meta("mandate.registered", "loan", "loan-1"))
                        .setMandateId("mand-1")
                        .setLoanId("loan-1")
                        .setCustomerId("cust-1")
                        .setStatus("ACTIVE")
                        .setRegisteredAt(Instant.parse("2026-10-20T06:00:00.000Z"))
                        .build();
        assertRoundTrips("repayment.events", event);
    }

    @Test
    void mandateDebitFailedRoundTripsWithAndWithoutRePresentation() {
        MandateDebitFailed scheduled =
                MandateDebitFailed.newBuilder()
                        .setMetadata(meta("mandate.debit-failed", "loan", "loan-1"))
                        .setMandateId("mand-1")
                        .setLoanId("loan-1")
                        .setAmount(inr(474_000L))
                        .setDueDate(LocalDate.of(2026, 11, 13))
                        .setFailureCode("INSUFFICIENT_FUNDS")
                        .setRePresentOn(LocalDate.of(2026, 11, 17))
                        .setFailedAt(Instant.parse("2026-11-13T08:00:00.000Z"))
                        .build();
        MandateDebitFailed terminal =
                MandateDebitFailed.newBuilder(scheduled).setRePresentOn(null).build();

        assertRoundTrips("repayment.events", scheduled);
        assertRoundTrips("repayment.events", terminal);
    }

    @Test
    void loanRepaymentReversedRoundTrips() {
        LoanRepaymentReversed event =
                LoanRepaymentReversed.newBuilder()
                        .setMetadata(meta("loan.repayment-reversed", "loan", "loan-1"))
                        .setLoanId("loan-1")
                        .setRepaymentId("rep-1")
                        .setTotalReversed(inr(474_000L))
                        .setPenal(inr(0L))
                        .setInterest(inr(100_000L))
                        .setPrincipal(inr(374_000L))
                        .setAdvance(inr(0L))
                        .setReversedAt(Instant.parse("2026-11-15T07:00:01.000Z"))
                        .build();
        assertRoundTrips("loan.events", event);
    }

    @Test
    void loanForeclosedRoundTrips() {
        LoanForeclosed event =
                LoanForeclosed.newBuilder()
                        .setMetadata(meta("loan.foreclosed", "loan", "loan-1"))
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setRepaymentId("rep-fc")
                        .setForeclosureAmount(inr(4_900_000L))
                        .setForeclosedAt(Instant.parse("2026-11-13T09:00:00.000Z"))
                        .build();
        assertRoundTrips("loan.events", event);
    }
}
