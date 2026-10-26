package com.lms.collections.config;

import com.lms.collections.application.CaseQueryService;
import com.lms.collections.application.CollectionsService;
import com.lms.collections.application.port.out.CollectionCaseRepository;
import com.lms.collections.application.port.out.CollectionsEventPublisher;
import com.lms.collections.application.port.out.DunningActionRepository;
import com.lms.collections.application.port.out.DunningPlanProvider;
import com.lms.collections.application.port.out.DunningWorkflowClient;
import com.lms.events.AvroEventSerde;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import java.time.Clock;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires collections: outbox/inbox, Avro serde and the application services. */
@Configuration
@EnableConfigurationProperties(DunningPlanProperties.class)
public class CollectionsConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

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
    public CollectionsService collectionsService(
            CollectionCaseRepository cases,
            DunningWorkflowClient workflow,
            CollectionsEventPublisher publisher,
            DunningPlanProvider plans,
            Clock clock) {
        return new CollectionsService(cases, workflow, publisher, plans, clock);
    }

    @Bean
    public CaseQueryService caseQueryService(
            CollectionCaseRepository cases, DunningActionRepository actions) {
        return new CaseQueryService(cases, actions);
    }
}
