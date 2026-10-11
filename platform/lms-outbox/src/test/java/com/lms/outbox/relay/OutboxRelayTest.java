package com.lms.outbox.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxRelayTest {

    @Test
    void publishesBatchAndMarksEachPublished() {
        RecordingRepository repo = new RecordingRepository(record("a"), record("b"));
        List<UUID> sent = new ArrayList<>();
        RecordSender sender = r -> sent.add(r.id());

        int published = new OutboxRelay(repo, sender, 10).drainOnce();

        assertThat(published).isEqualTo(2);
        assertThat(sent).hasSize(2);
        assertThat(repo.publishedIds).hasSize(2);
        assertThat(repo.failures).isEmpty();
    }

    @Test
    void recordsFailureWhenSendThrowsAndContinues() {
        OutboxRecord bad = record("bad");
        OutboxRecord good = record("good");
        RecordingRepository repo = new RecordingRepository(bad, good);
        RecordSender sender =
                r -> {
                    if (r.id().equals(bad.id())) {
                        throw new IllegalStateException("broker down");
                    }
                };

        int published = new OutboxRelay(repo, sender, 10).drainOnce();

        assertThat(published).isEqualTo(1);
        assertThat(repo.publishedIds).containsExactly(good.id());
        assertThat(repo.failures).containsKey(bad.id());
    }

    private static OutboxRecord record(String tag) {
        return new OutboxRecord(
                UUID.randomUUID(),
                "customer",
                tag,
                "customer.registered",
                UUID.randomUUID(),
                "customer.events",
                tag.getBytes(),
                ("payload-" + tag).getBytes(),
                Map.of(),
                0,
                null,
                Instant.now(),
                null);
    }

    private static final class RecordingRepository implements OutboxRepository {
        private final List<OutboxRecord> batch;
        final List<UUID> publishedIds = new ArrayList<>();
        final java.util.Map<UUID, String> failures = new java.util.HashMap<>();

        RecordingRepository(OutboxRecord... records) {
            this.batch = new ArrayList<>(List.of(records));
        }

        @Override
        public void append(OutboxRecord record) {
            batch.add(record);
        }

        @Override
        public List<OutboxRecord> lockUnpublishedBatch(int limit) {
            return batch.stream().filter(r -> !publishedIds.contains(r.id())).limit(limit).toList();
        }

        @Override
        public void markPublished(UUID id) {
            publishedIds.add(id);
        }

        @Override
        public void recordFailure(UUID id, String error) {
            failures.put(id, error);
        }
    }
}
