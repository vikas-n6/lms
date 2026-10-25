package com.lms.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.collections.CollectionsCaseClosed;
import com.lms.contracts.collections.CollectionsCaseOpened;
import com.lms.contracts.collections.CollectionsEscalated;
import com.lms.contracts.collections.CollectionsReminderDue;
import com.lms.contracts.events.EventMetadata;
import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.ledger.LedgerReconCompleted;
import com.lms.contracts.loan.LoanDpdUpdated;
import com.lms.contracts.loan.LoanInstallmentDueSoon;
import com.lms.contracts.loan.LoanPenalAccrued;
import com.lms.contracts.loan.LoanWrittenOff;
import com.lms.contracts.notification.NotificationFailed;
import com.lms.contracts.notification.NotificationSent;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

/**
 * Serde round-trips for the Phase 4 collections/operations events (penal accrual, DPD, due-soon,
 * written-off, collections.*, notification.*) and the additive {@code LedgerReconCompleted}
 * extension. Confirms the new {@code reconType}/count fields default (BACKWARD-compatible) and that
 * every event round-trips through the Schema-Registry serde with no running registry, so it runs on
 * JDK 25 without Docker.
 */
class Phase4EventsSerdeTest {

    private static MoneyAmount inr(long minor) {
        return MoneyAmount.newBuilder().setMinorUnits(minor).setCurrency("INR").build();
    }

    private static EventMetadata meta(String type, String aggregateType, String aggregateId) {
        return new EventMetadata(
                "018f9a00-0000-7000-8000-000000000041",
                type,
                1,
                Instant.parse("2026-12-01T04:30:00.000Z"),
                aggregateType,
                aggregateId,
                "corr-41",
                null,
                "trace-p4",
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
    void loanPenalAccruedRoundTrips() {
        LoanPenalAccrued event =
                LoanPenalAccrued.newBuilder()
                        .setMetadata(meta("loan.penal-accrued", "loan", "loan-1"))
                        .setLoanId("loan-1")
                        .setBusinessDate(LocalDate.of(2026, 12, 1))
                        .setAmount(inr(47L))
                        .setOverdueBase(inr(474_000L))
                        .setAccruedAt(Instant.parse("2026-12-01T04:30:00.000Z"))
                        .build();
        assertRoundTrips("loan.events", event);
    }

    @Test
    void loanDpdUpdatedRoundTrips() {
        LoanDpdUpdated event =
                LoanDpdUpdated.newBuilder()
                        .setMetadata(meta("loan.dpd-updated", "loan", "loan-1"))
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setBusinessDate(LocalDate.of(2026, 12, 1))
                        .setDaysPastDue(7)
                        .setBucket("DPD_1_30")
                        .setOverduePrincipal(inr(374_000L))
                        .setOverdueInterest(inr(100_000L))
                        .setOverduePenal(inr(329L))
                        .setOverdueTotal(inr(474_329L))
                        .setNpa(false)
                        .setUpdatedAt(Instant.parse("2026-12-01T04:30:00.000Z"))
                        .build();
        assertRoundTrips("loan.events", event);
    }

    @Test
    void loanInstallmentDueSoonRoundTrips() {
        LoanInstallmentDueSoon event =
                LoanInstallmentDueSoon.newBuilder()
                        .setMetadata(meta("loan.installment-due-soon", "loan", "loan-1"))
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setInstalmentNumber(2)
                        .setDueDate(LocalDate.of(2026, 12, 13))
                        .setAmountDue(inr(474_000L))
                        .setNotifiedAt(Instant.parse("2026-12-10T04:30:00.000Z"))
                        .build();
        assertRoundTrips("loan.events", event);
    }

    @Test
    void loanWrittenOffRoundTrips() {
        LoanWrittenOff event =
                LoanWrittenOff.newBuilder()
                        .setMetadata(meta("loan.written-off", "loan", "loan-1"))
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setRequestId("wor-1")
                        .setPrincipal(inr(3_000_000L))
                        .setInterest(inr(200_000L))
                        .setPenal(inr(50_000L))
                        .setTotal(inr(3_250_000L))
                        .setReason("NPA 180+ dpd, uncollectable")
                        .setRequestedBy("admin-a")
                        .setApprovedBy("admin-b")
                        .setWrittenOffAt(Instant.parse("2027-06-01T04:30:00.000Z"))
                        .build();
        assertRoundTrips("loan.events", event);
    }

    @Test
    void collectionsEventsRoundTrip() {
        CollectionsCaseOpened opened =
                CollectionsCaseOpened.newBuilder()
                        .setMetadata(meta("collections.case-opened", "loan", "loan-1"))
                        .setCaseId("case-1")
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setBucket("DPD_1_30")
                        .setDaysPastDue(1)
                        .setPlanVersion(1)
                        .setOpenedAt(Instant.parse("2026-12-01T04:30:00.000Z"))
                        .build();
        CollectionsReminderDue reminder =
                CollectionsReminderDue.newBuilder()
                        .setMetadata(meta("collections.reminder-due", "loan", "loan-1"))
                        .setCaseId("case-1")
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setStepId("DPD_7_SMS_REPRESENT")
                        .setDpdThreshold(7)
                        .setActionType("SMS_REPRESENT")
                        .setChannel("SMS")
                        .setTemplateId("dunning.dpd7")
                        .setFiredAt(Instant.parse("2026-12-07T04:30:00.000Z"))
                        .build();
        CollectionsEscalated escalated =
                CollectionsEscalated.newBuilder()
                        .setMetadata(meta("collections.escalated", "loan", "loan-1"))
                        .setCaseId("case-1")
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setStatus("ESCALATED")
                        .setStepId("DPD_15_ESCALATE")
                        .setReason("DPD 15 reached")
                        .setEscalatedAt(Instant.parse("2026-12-15T04:30:00.000Z"))
                        .build();
        CollectionsCaseClosed closed =
                CollectionsCaseClosed.newBuilder()
                        .setMetadata(meta("collections.case-closed", "loan", "loan-1"))
                        .setCaseId("case-1")
                        .setLoanId("loan-1")
                        .setLoanNumber("LN-2026-000001")
                        .setStatus("RESOLVED")
                        .setReason("overdue cleared")
                        .setClosedAt(Instant.parse("2026-12-20T04:30:00.000Z"))
                        .build();

        assertThat(escalated.getStepId()).isEqualTo("DPD_15_ESCALATE");
        assertRoundTrips("collections.events", opened);
        assertRoundTrips("collections.events", reminder);
        assertRoundTrips("collections.events", escalated);
        assertRoundTrips("collections.events", closed);
    }

    @Test
    void notificationEventsRoundTrip() {
        NotificationSent sent =
                NotificationSent.newBuilder()
                        .setMetadata(meta("notification.sent", "notification", "notif-1"))
                        .setNotificationId("notif-1")
                        .setTriggerEventId("evt-1")
                        .setTemplateId("loan.activated")
                        .setChannel("SMS")
                        .setSentAt(Instant.parse("2026-12-01T04:30:00.000Z"))
                        .build();
        NotificationFailed failed =
                NotificationFailed.newBuilder()
                        .setMetadata(meta("notification.failed", "notification", "notif-2"))
                        .setNotificationId("notif-2")
                        .setTriggerEventId("evt-2")
                        .setTemplateId("collections.dpd7")
                        .setChannel("WHATSAPP")
                        .setAttempts(3)
                        .setFailureCode("CHANNEL_UNAVAILABLE")
                        .setFailedAt(Instant.parse("2026-12-01T04:35:00.000Z"))
                        .build();
        assertRoundTrips("notification.events", sent);
        assertRoundTrips("notification.events", failed);
    }

    @Test
    void ledgerReconCompletedDefaultsTrialBalanceFields() {
        // Historical TRIAL_BALANCE publish: the new fields default, so it stays BACKWARD-compatible.
        LedgerReconCompleted trial =
                LedgerReconCompleted.newBuilder()
                        .setMetadata(meta("ledger.recon-completed", "reconRun", "run-1"))
                        .setReconRunId("run-1")
                        .setCurrency("INR")
                        .setDiffMinorUnits(0L)
                        .setBalanced(true)
                        .setRunAt(Instant.parse("2026-12-01T04:30:00.000Z"))
                        .build();
        assertThat(trial.getReconType()).isEqualTo("TRIAL_BALANCE");
        assertThat(trial.getMatched()).isZero();

        LedgerReconCompleted bankRail =
                LedgerReconCompleted.newBuilder()
                        .setMetadata(meta("ledger.recon-completed", "reconRun", "run-2"))
                        .setReconRunId("run-2")
                        .setCurrency("INR")
                        .setDiffMinorUnits(0L)
                        .setBalanced(false)
                        .setRunAt(Instant.parse("2026-12-01T04:30:00.000Z"))
                        .setReconType("BANK_RAIL")
                        .setMatched(10)
                        .setMissingInLedger(1)
                        .setMissingInBank(1)
                        .setAmountMismatch(1)
                        .setDuplicate(1)
                        .build();
        assertRoundTrips("ledger.events", trial);
        assertRoundTrips("ledger.events", bankRail);
    }
}
