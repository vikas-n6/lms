package com.lms.gateway.dlt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dead-letter viewer and replay (phase prompt task 2; docs/architecture.md "Failures": the Admin UI
 * lists DLT messages with a replay button). Reads {@code .dlt} topics as raw bytes + headers — no
 * Avro, so listing never needs a schema — and replays a record by re-producing its value, key and
 * original headers to the original topic (from the {@code kafka_dlt-original-topic} header, else by
 * stripping the {@code .dlt} suffix). Admin-only (gateway security: {@code /api/admin/**}). Replay
 * is an admin action; auditing it through an outbox is a backlog item (the gateway has no
 * database).
 */
@RestController
@RequestMapping("/api/admin/dlt")
public class DltAdminController {

    private static final Logger log = LoggerFactory.getLogger(DltAdminController.class);
    private static final String ORIGINAL_TOPIC_HEADER = "kafka_dlt-original-topic";
    private static final String EXCEPTION_FQCN_HEADER = "kafka_dlt-exception-fqcn";
    private static final String EXCEPTION_MESSAGE_HEADER = "kafka_dlt-exception-message";
    private static final String ORIGINAL_PARTITION_HEADER = "kafka_dlt-original-partition";
    private static final Duration POLL = Duration.ofSeconds(2);

    private final String bootstrap;

    public DltAdminController(
            @Value("${lms.kafka.bootstrap-servers:localhost:9092}") String bootstrap) {
        this.bootstrap = bootstrap;
    }

    @GetMapping("/{topic}")
    public List<DltRecordView> list(
            @PathVariable String topic, @RequestParam(defaultValue = "50") int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        List<DltRecordView> out = new ArrayList<>();
        try (KafkaConsumer<byte[], byte[]> consumer = consumer()) {
            List<TopicPartition> partitions = partitionsOf(consumer, topic);
            if (partitions.isEmpty()) {
                return out;
            }
            consumer.assign(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            Map<TopicPartition, Long> begins = consumer.beginningOffsets(partitions);
            for (TopicPartition tp : partitions) {
                long start = Math.max(begins.get(tp), ends.get(tp) - cap);
                consumer.seek(tp, start);
            }
            ConsumerRecords<byte[], byte[]> records = consumer.poll(POLL);
            for (ConsumerRecord<byte[], byte[]> r : records) {
                out.add(toView(r));
            }
        }
        out.sort(Comparator.comparingLong(DltRecordView::timestamp).reversed());
        return out.size() > cap ? out.subList(0, cap) : out;
    }

    @PostMapping("/{topic}/replay")
    public ResponseEntity<ReplayResult> replay(
            @PathVariable String topic, @RequestBody ReplayRequest request) {
        TopicPartition tp = new TopicPartition(topic, request.partition());
        try (KafkaConsumer<byte[], byte[]> consumer = consumer()) {
            consumer.assign(List.of(tp));
            consumer.seek(tp, request.offset());
            ConsumerRecords<byte[], byte[]> records = consumer.poll(POLL);
            for (ConsumerRecord<byte[], byte[]> r : records) {
                if (r.offset() != request.offset()) {
                    continue;
                }
                String originalTopic = header(r, ORIGINAL_TOPIC_HEADER);
                if (originalTopic == null) {
                    originalTopic =
                            topic.endsWith(".dlt") ? topic.substring(0, topic.length() - 4) : topic;
                }
                reproduce(originalTopic, r);
                log.info("replayed DLT record {}-{} to {}", topic, request.offset(), originalTopic);
                return ResponseEntity.ok(new ReplayResult(topic, request.offset(), originalTopic));
            }
        }
        return ResponseEntity.notFound().build();
    }

    private void reproduce(String originalTopic, ConsumerRecord<byte[], byte[]> r) {
        RecordHeaders headers = new RecordHeaders();
        for (Header h : r.headers()) {
            // Carry the original headers; drop the DLT bookkeeping ones so a re-DLT is clean.
            if (!h.key().startsWith("kafka_dlt-")) {
                headers.add(h);
            }
        }
        try (KafkaProducer<byte[], byte[]> producer = producer()) {
            producer.send(new ProducerRecord<>(originalTopic, null, r.key(), r.value(), headers));
            producer.flush();
        }
    }

    private List<TopicPartition> partitionsOf(
            KafkaConsumer<byte[], byte[]> consumer, String topic) {
        List<PartitionInfo> infos = consumer.partitionsFor(topic);
        List<TopicPartition> partitions = new ArrayList<>();
        if (infos != null) {
            for (PartitionInfo info : infos) {
                partitions.add(new TopicPartition(topic, info.partition()));
            }
        }
        return partitions;
    }

    private static DltRecordView toView(ConsumerRecord<byte[], byte[]> r) {
        return new DltRecordView(
                r.partition(),
                r.offset(),
                r.timestamp(),
                r.key() == null ? null : new String(r.key(), StandardCharsets.UTF_8),
                header(r, ORIGINAL_TOPIC_HEADER),
                header(r, ORIGINAL_PARTITION_HEADER),
                header(r, EXCEPTION_FQCN_HEADER),
                header(r, EXCEPTION_MESSAGE_HEADER));
    }

    private static String header(ConsumerRecord<byte[], byte[]> r, String key) {
        Header h = r.headers().lastHeader(key);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private KafkaConsumer<byte[], byte[]> consumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "gateway-dlt-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class.getName());
        props.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }

    private KafkaProducer<byte[], byte[]> producer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    /** A DLT record for the viewer: position, time, key and the failure headers. */
    public record DltRecordView(
            int partition,
            long offset,
            long timestamp,
            String key,
            String originalTopic,
            String originalPartition,
            String exceptionClass,
            String exceptionMessage) {}

    /** Replay request body: which record (partition + offset) on the named .dlt topic to replay. */
    public record ReplayRequest(int partition, long offset) {}

    /** Replay result: the record replayed and the topic it was re-produced to. */
    public record ReplayResult(String dltTopic, long offset, String replayedTo) {}
}
