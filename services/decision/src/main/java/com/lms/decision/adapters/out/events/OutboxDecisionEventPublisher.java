package com.lms.decision.adapters.out.events;

import com.lms.contracts.decision.DecisionCompleted;
import com.lms.contracts.events.MoneyAmount;
import com.lms.decision.application.port.out.DecisionEventPublisher;
import com.lms.decision.domain.Decision;
import com.lms.decision.domain.ReasonCode;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.money.Money;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.security.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

/**
 * Writes {@code decision.completed} to the outbox in the same transaction as the decision save
 * (docs/architecture.md). Keyed by the application id so decision events stay ordered per
 * application.
 */
@Component
public class OutboxDecisionEventPublisher implements DecisionEventPublisher {

    static final String TOPIC = "decision.events";
    static final String EVENT_TYPE = "decision.completed";
    private static final String PRODUCER = "decision";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxDecisionEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = outbox;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Override
    public void decisionCompleted(Decision decision) {
        String aggregateId = decision.applicationId();
        EventMeta meta =
                EventMeta.newEvent(
                        clock,
                        EVENT_TYPE,
                        SCHEMA_VERSION,
                        "application",
                        aggregateId,
                        aggregateId,
                        null,
                        RequestContextHolder.current().traceId(),
                        PRODUCER);

        DecisionCompleted event =
                DecisionCompleted.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setDecisionId(decision.id().toString())
                        .setApplicationId(decision.applicationId())
                        .setCustomerId(decision.customerId())
                        .setOutcome(decision.outcome().name())
                        .setReasonCodes(reasonCodes(decision.reasonCodes()))
                        .setRuleSetVersion(decision.ruleSetVersion())
                        .setOfferedLimit(
                                decision.offeredLimit()
                                        .map(OutboxDecisionEventPublisher::money)
                                        .orElse(null))
                        .setBureauScore(
                                decision.bureauSnapshot().available()
                                        ? decision.bureauSnapshot().score()
                                        : null)
                        .setManualOverride(decision.manualOverride())
                        .setDecidedAt(decision.decidedAt())
                        .build();

        byte[] payload = serializer.serialize(TOPIC, event);
        OutboxRecord row =
                new OutboxRecord(
                        Uuid7.generate(clock),
                        "application",
                        aggregateId,
                        EVENT_TYPE,
                        meta.eventId(),
                        TOPIC,
                        aggregateId.getBytes(StandardCharsets.UTF_8),
                        payload,
                        EventHeaders.toHeaders(meta),
                        0,
                        null,
                        clock.instant(),
                        null);
        outbox.append(row);
    }

    private static List<String> reasonCodes(List<ReasonCode> codes) {
        return codes.stream().map(Enum::name).toList();
    }

    private static MoneyAmount money(Money m) {
        return MoneyAmount.newBuilder()
                .setMinorUnits(m.minorUnits())
                .setCurrency(m.currency().getCurrencyCode())
                .build();
    }
}
