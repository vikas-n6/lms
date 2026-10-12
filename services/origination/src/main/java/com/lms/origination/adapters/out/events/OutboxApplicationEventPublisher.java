package com.lms.origination.adapters.out.events;

import com.lms.contracts.application.ApplicantSnapshot;
import com.lms.contracts.application.ApplicationAccepted;
import com.lms.contracts.application.ApplicationExpired;
import com.lms.contracts.application.ApplicationOffered;
import com.lms.contracts.application.ApplicationRejected;
import com.lms.contracts.application.ApplicationSubmitted;
import com.lms.contracts.application.OfferOption;
import com.lms.contracts.events.MoneyAmount;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.money.Money;
import com.lms.origination.application.ApplicantSnapshotData;
import com.lms.origination.application.port.out.ApplicationEventPublisher;
import com.lms.origination.domain.AcceptedTerms;
import com.lms.origination.domain.Application;
import com.lms.origination.domain.Offer;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.schedule.OfferQuote;
import com.lms.security.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

/** Writes the application lifecycle events to the outbox (docs/architecture.md). */
@Component
public class OutboxApplicationEventPublisher implements ApplicationEventPublisher {

    static final String TOPIC = "application.events";
    private static final String PRODUCER = "origination";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxApplicationEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = outbox;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Override
    public void submitted(Application app, ApplicantSnapshotData s) {
        EventMeta meta = meta("application.submitted", app.id().toString());
        ApplicantSnapshot snapshot =
                ApplicantSnapshot.newBuilder()
                        .setAgeYears(s.ageYears())
                        .setKycStatus(s.kycStatus())
                        .setBankAccountLinked(s.bankAccountLinked())
                        .setDeclaredMonthlyIncome(money(s.declaredMonthlyIncome()))
                        .setEmploymentType(s.employmentType())
                        .setExistingMonthlyEmi(money(s.existingMonthlyEmi()))
                        .setActiveLoanCount(s.activeLoanCount())
                        .setPhoneBlindIndex(s.phoneBlindIndex())
                        .setBureauSubjectPhone(s.bureauSubjectPhone())
                        .setDataProcessingConsent(s.dataProcessingConsent())
                        .setCreditPullConsent(s.creditPullConsent())
                        .setLoanAgreementConsent(s.loanAgreementConsent())
                        .build();
        append(
                meta,
                app,
                ApplicationSubmitted.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setApplicationId(app.id().toString())
                        .setCustomerId(app.customerId())
                        .setProductId(app.productId())
                        .setRequestedAmount(money(app.requestedAmount()))
                        .setSnapshot(snapshot)
                        .setSubmittedAt(clock.instant())
                        .build());
    }

    @Override
    public void offered(Application app) {
        Offer offer = app.offer().orElseThrow();
        EventMeta meta = meta("application.offered", app.id().toString());
        List<OfferOption> options = offer.options().stream().map(this::option).toList();
        append(
                meta,
                app,
                ApplicationOffered.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setApplicationId(app.id().toString())
                        .setCustomerId(app.customerId())
                        .setPrincipal(money(offer.options().get(0).principal()))
                        .setOptions(options)
                        .setOfferExpiresAt(offer.expiresAt())
                        .setOfferedAt(offer.offeredAt())
                        .build());
    }

    @Override
    public void accepted(Application app) {
        AcceptedTerms t = app.acceptedTerms().orElseThrow();
        EventMeta meta = meta("application.accepted", app.id().toString());
        append(
                meta,
                app,
                ApplicationAccepted.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setApplicationId(app.id().toString())
                        .setCustomerId(app.customerId())
                        .setProductId(app.productId())
                        .setPrincipal(money(t.principal()))
                        .setTenorMonths(t.tenorMonths())
                        .setEmi(money(t.emi()))
                        .setFee(money(t.fee()))
                        .setGst(money(t.gst()))
                        .setNetDisbursal(money(t.netDisbursal()))
                        .setEffectiveAnnualRatePercent(t.effectiveAnnualRatePercent())
                        .setEsignReference(t.esignReference())
                        .setAcceptedAt(clock.instant())
                        .build());
    }

    @Override
    public void rejected(Application app, List<String> reasonCodes) {
        EventMeta meta = meta("application.rejected", app.id().toString());
        append(
                meta,
                app,
                ApplicationRejected.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setApplicationId(app.id().toString())
                        .setCustomerId(app.customerId())
                        .setReasonCodes(List.copyOf(reasonCodes))
                        .setRejectedAt(clock.instant())
                        .build());
    }

    @Override
    public void expired(Application app) {
        EventMeta meta = meta("application.expired", app.id().toString());
        append(
                meta,
                app,
                ApplicationExpired.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setApplicationId(app.id().toString())
                        .setCustomerId(app.customerId())
                        .setExpiredAt(clock.instant())
                        .build());
    }

    private OfferOption option(OfferQuote q) {
        return OfferOption.newBuilder()
                .setTenorMonths(q.tenor())
                .setEmi(money(q.emi()))
                .setFee(money(q.fee()))
                .setGst(money(q.gst()))
                .setNetDisbursal(money(q.netDisbursal()))
                .setTotalPayable(money(q.totalPayable()))
                .setEffectiveAnnualRatePercent(q.effectiveAnnualRate().asPlainString())
                .build();
    }

    private EventMeta meta(String eventType, String aggregateId) {
        return EventMeta.newEvent(
                clock,
                eventType,
                SCHEMA_VERSION,
                "application",
                aggregateId,
                aggregateId,
                null,
                RequestContextHolder.current().traceId(),
                PRODUCER);
    }

    private void append(EventMeta meta, Application app, SpecificRecord event) {
        String aggregateId = app.id().toString();
        byte[] payload = serializer.serialize(TOPIC, event);
        outbox.append(
                new OutboxRecord(
                        Uuid7.generate(clock),
                        "application",
                        aggregateId,
                        meta.eventType(),
                        meta.eventId(),
                        TOPIC,
                        aggregateId.getBytes(StandardCharsets.UTF_8),
                        payload,
                        EventHeaders.toHeaders(meta),
                        0,
                        null,
                        clock.instant(),
                        null));
    }

    private static MoneyAmount money(Money m) {
        return MoneyAmount.newBuilder()
                .setMinorUnits(m.minorUnits())
                .setCurrency(m.currency().getCurrencyCode())
                .build();
    }
}
