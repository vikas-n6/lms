package com.lms.gateway.stream;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events fan-out for live dashboard updates (phase prompt task 2): the gateway holds
 * the open SSE connections and relays messages published on a Redis pub/sub channel (see {@link
 * DashboardStreamConfig}) to every connected back-office client. Kafka remains the source of truth
 * (docs/architecture.md "Redis rules": Admin live updates are Redis pub/sub fanned out to SSE). The
 * gateway runs on virtual threads, so one blocking emitter per client is fine (never pooled).
 */
@RestController
public class DashboardStreamController {

    private static final Logger log = LoggerFactory.getLogger(DashboardStreamController.class);
    private static final long NO_TIMEOUT =
            0L; // the client reconnects; we never time the stream out

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/api/stream/dashboard", produces = "text/event-stream")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Relay one message (from the Redis channel) to every connected client. */
    public void broadcast(String payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("dashboard").data(payload));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("dropped a closed SSE client", e);
            }
        }
    }

    int connectionCount() {
        return emitters.size();
    }
}
