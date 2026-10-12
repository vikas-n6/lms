package com.lms.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.application.ApplicantSnapshot;
import com.lms.contracts.application.ApplicationSubmitted;
import com.lms.contracts.decision.DecisionCompleted;
import com.lms.contracts.events.EventMetadata;
import com.lms.contracts.events.MoneyAmount;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

/**
 * Serde round-trips for the Phase 1 application/decision events, exercising a nested record, a
 * shared MoneyAmount, an array and a nullable union through the Schema-Registry serde with no
 * running registry (MockSchemaRegistryClient), so it runs on JDK 25 without Docker.
 */
class ApplicationEventsSerdeTest {

    private static MoneyAmount inr(long minor) {
        return MoneyAmount.newBuilder().setMinorUnits(minor).setCurrency("INR").build();
    }

    private static EventMetadata meta(String type, String aggregateType, String aggregateId) {
        return new EventMetadata(
                "018f9a00-0000-7000-8000-000000000009",
                type,
                1,
                Instant.parse("2026-10-11T10:15:30.123Z"),
                aggregateType,
                aggregateId,
                "corr-9",
                null,
                "trace-xyz",
                "origination");
    }

    @Test
    void applicationSubmittedRoundTripsWithNestedSnapshot() {
        SchemaRegistryClient registry = new MockSchemaRegistryClient();
        Serializer<Object> serializer = AvroEventSerde.serializer(registry, true);
        Deserializer<Object> deserializer = AvroEventSerde.deserializer(registry);

        ApplicantSnapshot snapshot =
                ApplicantSnapshot.newBuilder()
                        .setAgeYears(34)
                        .setKycStatus("VERIFIED")
                        .setBankAccountLinked(true)
                        .setDeclaredMonthlyIncome(inr(5_000_000L))
                        .setEmploymentType("SALARIED")
                        .setExistingMonthlyEmi(inr(0L))
                        .setActiveLoanCount(0)
                        .setPhoneBlindIndex("hmac-abc123")
                        .setBureauSubjectPhone("9000000001")
                        .setDataProcessingConsent(true)
                        .setCreditPullConsent(true)
                        .setLoanAgreementConsent(true)
                        .build();
        ApplicationSubmitted event =
                ApplicationSubmitted.newBuilder()
                        .setMetadata(meta("application.submitted", "application", "app-1"))
                        .setApplicationId("app-1")
                        .setCustomerId("cust-1")
                        .setProductId("MICRO-V1")
                        .setRequestedAmount(inr(3_000_000L))
                        .setSnapshot(snapshot)
                        .setSubmittedAt(Instant.parse("2026-10-11T10:15:30.123Z"))
                        .build();

        byte[] bytes = serializer.serialize("application.events", event);
        Object decoded = deserializer.deserialize("application.events", bytes);

        assertThat(decoded).isInstanceOf(ApplicationSubmitted.class).isEqualTo(event);
    }

    @Test
    void decisionCompletedRoundTripsWithNullableLimitAndReasonArray() {
        SchemaRegistryClient registry = new MockSchemaRegistryClient();
        Serializer<Object> serializer = AvroEventSerde.serializer(registry, true);
        Deserializer<Object> deserializer = AvroEventSerde.deserializer(registry);

        DecisionCompleted approve =
                DecisionCompleted.newBuilder()
                        .setMetadata(meta("decision.completed", "application", "app-1"))
                        .setDecisionId("dec-1")
                        .setApplicationId("app-1")
                        .setCustomerId("cust-1")
                        .setOutcome("APPROVE")
                        .setReasonCodes(List.of())
                        .setRuleSetVersion("v1")
                        .setOfferedLimit(inr(3_000_000L))
                        .setBureauScore(760)
                        .setManualOverride(false)
                        .setDecidedAt(Instant.parse("2026-10-11T10:15:31.000Z"))
                        .build();
        DecisionCompleted reject =
                DecisionCompleted.newBuilder()
                        .setMetadata(meta("decision.completed", "application", "app-2"))
                        .setDecisionId("dec-2")
                        .setApplicationId("app-2")
                        .setCustomerId("cust-2")
                        .setOutcome("REJECT")
                        .setReasonCodes(List.of("BUREAU_SCORE_LOW"))
                        .setRuleSetVersion("v1")
                        .setOfferedLimit(null)
                        .setBureauScore(540)
                        .setManualOverride(false)
                        .setDecidedAt(Instant.parse("2026-10-11T10:15:31.000Z"))
                        .build();

        for (DecisionCompleted event : List.of(approve, reject)) {
            byte[] bytes = serializer.serialize("decision.events", event);
            Object decoded = deserializer.deserialize("decision.events", bytes);
            assertThat(decoded).isInstanceOf(DecisionCompleted.class).isEqualTo(event);
        }
    }
}
