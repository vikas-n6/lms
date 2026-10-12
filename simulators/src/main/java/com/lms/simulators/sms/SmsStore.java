package com.lms.simulators.sms;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** In-memory SMS outbox (phase prompt S2). Newest last; read back via the outbox endpoint. */
@Component
public class SmsStore {

    private final CopyOnWriteArrayList<SmsMessage> messages = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public SmsStore(Clock clock) {
        this.clock = clock;
    }

    public SmsMessage send(String to, String body) {
        SmsMessage message =
                new SmsMessage(UUID.randomUUID().toString(), to, body, clock.instant());
        messages.add(message);
        return message;
    }

    public List<SmsMessage> outbox() {
        return List.copyOf(messages);
    }

    public List<SmsMessage> outboxFor(String to) {
        return messages.stream().filter(m -> m.to().equals(to)).toList();
    }

    public void clear() {
        messages.clear();
    }
}
