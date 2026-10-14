package com.lms.ledger.config;

import com.lms.events.AvroEventSerde;
import com.lms.ledger.application.LedgerQueryService;
import com.lms.ledger.application.PostEntryService;
import com.lms.ledger.application.ReconciliationService;
import com.lms.ledger.application.ReverseEntryService;
import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.application.port.out.LedgerEventPublisher;
import com.lms.ledger.domain.PostingTemplateRegistry;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import java.time.Clock;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the ledger service: outbox/inbox, Avro serde, posting templates and the use cases. */
@Configuration
public class LedgerConfig {

    @Bean
    public Serializer<Object> avroSerializer(
            @Value("${lms.kafka.schema-registry-url}") String schemaRegistryUrl) {
        return AvroEventSerde.serializer(schemaRegistryUrl, true);
    }

    @Bean
    public OutboxRepository outboxRepository(JdbcClient jdbcClient) {
        return new JdbcOutboxRepository(jdbcClient);
    }

    @Bean
    public InboxStore inboxStore(JdbcClient jdbcClient) {
        return new JdbcInboxStore(jdbcClient);
    }

    @Bean
    public IdempotentConsumer idempotentConsumer(InboxStore inboxStore) {
        return new IdempotentConsumer(inboxStore);
    }

    @Bean
    public PostingTemplateRegistry postingTemplateRegistry() {
        return PostingTemplateRegistry.defaults();
    }

    @Bean
    public PostEntryService postEntryService(
            JournalEntryRepository entries,
            PostingTemplateRegistry registry,
            LedgerEventPublisher publisher,
            Clock clock) {
        return new PostEntryService(entries, registry, publisher, clock);
    }

    @Bean
    public ReverseEntryService reverseEntryService(
            JournalEntryRepository entries, LedgerEventPublisher publisher, Clock clock) {
        return new ReverseEntryService(entries, publisher, clock);
    }

    @Bean
    public LedgerQueryService ledgerQueryService(JournalEntryRepository entries) {
        return new LedgerQueryService(entries);
    }

    @Bean
    public ReconciliationService reconciliationService(
            JournalEntryRepository entries,
            LedgerEventPublisher publisher,
            @Value("${lms.currency.default:INR}") String currencyCode) {
        return new ReconciliationService(entries, publisher, currencyCode);
    }
}
