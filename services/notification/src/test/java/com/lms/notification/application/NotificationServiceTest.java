package com.lms.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.notification.application.port.out.ChannelGateway;
import com.lms.notification.application.port.out.ConsentPolicy;
import com.lms.notification.application.port.out.NotificationEventPublisher;
import com.lms.notification.application.port.out.NotificationLogRepository;
import com.lms.notification.application.port.out.TemplateCatalog;
import com.lms.notification.domain.Channel;
import com.lms.notification.domain.NotificationStatus;
import com.lms.notification.domain.NotificationTemplate;
import com.lms.notification.domain.QuietHours;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Notification orchestration acceptance tests (phase prompt task 5): a replayed event sends no
 * second message, a message created at 22:00 is held and released the next morning, a failing
 * channel retries and ends FAILED after the configured attempts, and consent withheld suppresses.
 */
class NotificationServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Instant ist(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), IST).toInstant();
    }

    private final FakeCatalog catalog = new FakeCatalog();
    private final FakeLog log = new FakeLog();
    private final FakeGateway gateway = new FakeGateway();
    private final FakePublisher publisher = new FakePublisher();

    private NotificationService service(Clock clock, int maxAttempts, ConsentPolicy consent) {
        return new NotificationService(
                catalog,
                log,
                gateway,
                consent,
                publisher,
                new QuietHours(21, 8, IST),
                attempt -> {},
                clock,
                maxAttempts);
    }

    private static Clock fixed(Instant at) {
        return Clock.fixed(at, ZoneOffset.UTC);
    }

    @Test
    void sendsOncePerTemplateAndChannel() {
        NotificationService service = service(fixed(ist(2026, 11, 20, 10, 0)), 3, (r, c) -> true);
        catalog.put(
                "loan.activated",
                new NotificationTemplate("loan.activated", Channel.SMS, 1, "Hi {name}"));

        service.notify("evt-1", "loan.activated", "cust-1", Map.of("name", "A"));

        assertThat(gateway.sent).hasSize(1);
        assertThat(log.statuses.values()).containsOnly(NotificationStatus.SENT);
    }

    @Test
    void replayingAnEventSendsNoSecondMessage() {
        NotificationService service = service(fixed(ist(2026, 11, 20, 10, 0)), 3, (r, c) -> true);
        catalog.put(
                "loan.activated", new NotificationTemplate("loan.activated", Channel.SMS, 1, "Hi"));

        service.notify("evt-1", "loan.activated", "cust-1", Map.of());
        service.notify("evt-1", "loan.activated", "cust-1", Map.of()); // replay

        assertThat(gateway.sent).hasSize(1);
    }

    @Test
    void messageCreatedAt2200IsHeldThenReleasedNextMorning() {
        catalog.put(
                "loan.activated", new NotificationTemplate("loan.activated", Channel.SMS, 1, "Hi"));
        NotificationService night = service(fixed(ist(2026, 11, 20, 22, 0)), 3, (r, c) -> true);

        night.notify("evt-1", "loan.activated", "cust-1", Map.of());
        // Held, not delivered yet.
        assertThat(gateway.sent).isEmpty();
        assertThat(log.statuses.values()).containsOnly(NotificationStatus.QUEUED);

        // The next morning the release job delivers it.
        int released = night.releaseDue(ist(2026, 11, 21, 8, 0));
        assertThat(released).isEqualTo(1);
        assertThat(gateway.sent).hasSize(1);
        assertThat(log.statuses.values()).containsOnly(NotificationStatus.SENT);
    }

    @Test
    void failingChannelRetriesThenEndsFailedAfterConfiguredAttempts() {
        catalog.put(
                "disbursement.failed",
                new NotificationTemplate("disbursement.failed", Channel.WHATSAPP, 1, "failed"));
        gateway.alwaysFail = true;
        NotificationService service = service(fixed(ist(2026, 11, 20, 10, 0)), 3, (r, c) -> true);

        service.notify("evt-9", "disbursement.failed", "cust-1", Map.of());

        assertThat(gateway.attempts).isEqualTo(3); // maxAttempts
        assertThat(log.statuses.values()).containsOnly(NotificationStatus.FAILED);
        assertThat(publisher.failed).hasSize(1);
    }

    @Test
    void consentWithheldSuppressesWithoutSending() {
        catalog.put(
                "loan.activated", new NotificationTemplate("loan.activated", Channel.SMS, 1, "Hi"));
        NotificationService service = service(fixed(ist(2026, 11, 20, 10, 0)), 3, (r, c) -> false);

        service.notify("evt-1", "loan.activated", "cust-1", Map.of());

        assertThat(gateway.sent).isEmpty();
        assertThat(log.statuses.values()).containsOnly(NotificationStatus.SUPPRESSED);
    }

    // --- Fakes ---------------------------------------------------------------------------------

    private static final class FakeCatalog implements TemplateCatalog {
        private final Map<String, List<NotificationTemplate>> byEvent = new LinkedHashMap<>();

        void put(String eventType, NotificationTemplate template) {
            byEvent.computeIfAbsent(eventType, k -> new ArrayList<>()).add(template);
        }

        @Override
        public List<NotificationTemplate> templatesFor(String eventType) {
            return byEvent.getOrDefault(eventType, List.of());
        }
    }

    private static final class FakeLog implements NotificationLogRepository {
        record Key(String triggerEventId, String templateId, Channel channel) {}

        final Map<Key, UUID> byKey = new LinkedHashMap<>();
        final Map<UUID, NotificationStatus> statuses = new LinkedHashMap<>();
        final Map<UUID, Held> held = new LinkedHashMap<>();

        @Override
        public Optional<UUID> createIfAbsent(
                String triggerEventId,
                String templateId,
                Channel channel,
                String recipientRef,
                String body,
                Instant releaseAt,
                Instant now) {
            Key key = new Key(triggerEventId, templateId, channel);
            if (byKey.containsKey(key)) {
                return Optional.empty();
            }
            UUID id = UUID.randomUUID();
            byKey.put(key, id);
            statuses.put(id, NotificationStatus.QUEUED);
            if (releaseAt != null) {
                held.put(id, new Held(id, triggerEventId, templateId, channel, recipientRef, body));
            }
            return Optional.of(id);
        }

        @Override
        public void updateStatus(
                UUID id, NotificationStatus status, int attempts, String failureCode) {
            statuses.put(id, status);
            if (status != NotificationStatus.QUEUED) {
                held.remove(id);
            }
        }

        @Override
        public List<Held> findDue(Instant now) {
            return List.copyOf(held.values());
        }

        @Override
        public List<Row> list(Optional<String> status) {
            return List.of();
        }
    }

    private static final class FakeGateway implements ChannelGateway {
        final List<String> sent = new ArrayList<>();
        int attempts;
        boolean alwaysFail;

        @Override
        public void send(Channel channel, String recipientRef, String body) {
            attempts++;
            if (alwaysFail) {
                throw new ChannelDeliveryException("CHANNEL_UNAVAILABLE", "simulated failure");
            }
            sent.add(channel + ":" + body);
        }
    }

    private static final class FakePublisher implements NotificationEventPublisher {
        final List<UUID> sent = new ArrayList<>();
        final List<UUID> failed = new ArrayList<>();

        @Override
        public void sent(UUID id, String triggerEventId, String templateId, Channel channel) {
            sent.add(id);
        }

        @Override
        public void failed(
                UUID id,
                String triggerEventId,
                String templateId,
                Channel channel,
                int attempts,
                String failureCode) {
            failed.add(id);
        }
    }
}
