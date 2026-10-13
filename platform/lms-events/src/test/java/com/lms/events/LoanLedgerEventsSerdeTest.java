package com.lms.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.disbursement.DisbursementSucceeded;
import com.lms.contracts.events.EventMetadata;
import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.ledger.LedgerEntryPosted;
import com.lms.contracts.loan.LoanCreated;
import com.lms.contracts.loan.LoanInterestAccrued;
import com.lms.contracts.repayment.RepaymentReceived;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

/**
 * Serde round-trips for the Phase 2 loan, disbursement, repayment and ledger events, exercising
 * MoneyAmount, {@code date} logical types (LocalDate) and nullable unions through the
 * Schema-Registry serde with no running registry (MockSchemaRegistryClient), so it runs on JDK 25
 * without Docker.
 */
class LoanLedgerEventsSerdeTest {

    private static MoneyAmount inr(long minor) {
        return MoneyAmount.newBuilder().setMinorUnits(minor).setCurrency("INR").build();
    }

    private static EventMetadata meta(String type, String aggregateType, String aggregateId) {
        return new EventMetadata(
                "018f9a00-0000-7000-8000-000000000021",
                type,
                1,
                Instant.parse("2026-10-13T04:30:00.000Z"),
                aggregateType,
                aggregateId,
                "corr-21",
                null,
                "trace-loan",
                "loan-account");
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
    void loanCreatedRoundTrips() {
        LoanCreated event =
                LoanCreated.newBuilder()
                        .setMetadata(meta("loan.created", "loan", "loan-1"))
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setApplicationId("app-1")
                        .setCustomerId("cust-1")
                        .setProductId("MICRO-V1")
                        .setProductVersion(1)
                        .setPrincipal(inr(5_000_000L))
                        .setTenorMonths(12)
                        .setEmi(inr(474_000L))
                        .setFee(inr(100_000L))
                        .setGst(inr(18_000L))
                        .setNetDisbursal(inr(4_882_000L))
                        .setAnnualInterestRatePercent("24")
                        .setCreatedAt(Instant.parse("2026-10-13T04:30:00.000Z"))
                        .build();
        assertRoundTrips("loan.events", event);
    }

    @Test
    void disbursementSucceededRoundTripsWithDate() {
        DisbursementSucceeded event =
                DisbursementSucceeded.newBuilder()
                        .setMetadata(meta("disbursement.succeeded", "loan", "loan-1"))
                        .setDisbursementId("disb-1")
                        .setLoanId("loan-1")
                        .setPrincipal(inr(5_000_000L))
                        .setFee(inr(100_000L))
                        .setGst(inr(18_000L))
                        .setNetAmount(inr(4_882_000L))
                        .setDisbursementDate(LocalDate.of(2026, 10, 13))
                        .setRailReference("RAIL-123")
                        .setSucceededAt(Instant.parse("2026-10-13T05:00:00.000Z"))
                        .build();
        assertRoundTrips("disbursement.events", event);
    }

    @Test
    void loanInterestAccruedRoundTripsWithDate() {
        LoanInterestAccrued event =
                LoanInterestAccrued.newBuilder()
                        .setMetadata(meta("loan.interest-accrued", "loan", "loan-1"))
                        .setLoanId("loan-1")
                        .setBusinessDate(LocalDate.of(2026, 11, 1))
                        .setAmount(inr(3_288L))
                        .setInstalmentNumber(1)
                        .setAccruedAt(Instant.parse("2026-11-01T00:00:00.000Z"))
                        .build();
        assertRoundTrips("loan.events", event);
    }

    @Test
    void repaymentReceivedRoundTrips() {
        RepaymentReceived event =
                RepaymentReceived.newBuilder()
                        .setMetadata(meta("repayment.received", "loan", "loan-1"))
                        .setRepaymentId("rep-1")
                        .setLoanId("loan-1")
                        .setAmount(inr(474_000L))
                        .setRailReference("RAIL-777")
                        .setReceivedAt(Instant.parse("2026-11-13T06:00:00.000Z"))
                        .build();
        assertRoundTrips("repayment.events", event);
    }

    @Test
    void ledgerEntryPostedRoundTripsWithNullableDimensions() {
        LedgerEntryPosted withLoan =
                LedgerEntryPosted.newBuilder()
                        .setMetadata(meta("ledger.entry-posted", "journalEntry", "je-1"))
                        .setJournalEntryId("je-1")
                        .setSourceEventId("018f9a00-0000-7000-8000-000000000055")
                        .setSourceEventType("disbursement.succeeded")
                        .setLoanId("loan-1")
                        .setCustomerId("cust-1")
                        .setCurrency("INR")
                        .setTotalDebitMinorUnits(5_000_000L)
                        .setTotalCreditMinorUnits(5_000_000L)
                        .setPostedAt(Instant.parse("2026-10-13T05:00:01.000Z"))
                        .build();
        LedgerEntryPosted noDimensions =
                LedgerEntryPosted.newBuilder(withLoan).setLoanId(null).setCustomerId(null).build();

        assertRoundTrips("ledger.events", withLoan);
        assertRoundTrips("ledger.events", noDimensions);
    }
}
