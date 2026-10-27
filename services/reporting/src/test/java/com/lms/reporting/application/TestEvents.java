package com.lms.reporting.application;

import com.lms.contracts.application.ApplicantSnapshot;
import com.lms.contracts.events.EventMetadata;
import com.lms.contracts.events.MoneyAmount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Minimal builders for the Avro events the projections consume. */
final class TestEvents {

    static final String INR = "INR";

    private TestEvents() {}

    static EventMetadata meta(String eventType, String aggregateId) {
        return EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setSchemaVersion(1)
                .setOccurredAt(Instant.parse("2026-01-15T00:00:00Z"))
                .setAggregateType("loan")
                .setAggregateId(aggregateId)
                .setCorrelationId("corr-" + aggregateId)
                .setProducer("test")
                .build();
    }

    static MoneyAmount money(long minor) {
        return MoneyAmount.newBuilder().setMinorUnits(minor).setCurrency(INR).build();
    }

    static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    static ApplicantSnapshot snapshot() {
        return ApplicantSnapshot.newBuilder()
                .setAgeYears(30)
                .setKycStatus("VERIFIED")
                .setBankAccountLinked(true)
                .setDeclaredMonthlyIncome(money(50_000_00))
                .setEmploymentType("SALARIED")
                .setExistingMonthlyEmi(money(0))
                .setActiveLoanCount(0)
                .setPhoneBlindIndex("blind-index")
                .setBureauSubjectPhone("")
                .setDataProcessingConsent(true)
                .setCreditPullConsent(true)
                .setLoanAgreementConsent(true)
                .build();
    }
}
