package com.lms.simulators.notify;

import com.lms.simulators.behavior.SimBehavior;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic multi-channel notification simulator (phase prompt task 5): email, push, WhatsApp and SMS
 * go through one endpoint, each gated by its own channel name so failure rates can be set per
 * channel via the control API. Delivered messages are kept in an in-memory outbox for assertions.
 */
@RestController
@RequestMapping("/sim/notify")
public class NotifyController {

    private final SimBehavior behavior;
    private final Clock clock;
    private final CopyOnWriteArrayList<Delivered> outbox = new CopyOnWriteArrayList<>();

    public NotifyController(SimBehavior behavior, Clock clock) {
        this.behavior = behavior;
        this.clock = clock;
    }

    @PostMapping
    public Delivered send(@RequestBody SendRequest request) {
        String channel = request.channel().toLowerCase(Locale.ROOT);
        behavior.gate(channel); // may throw a channel-specific failure
        Delivered delivered =
                new Delivered(
                        UUID.randomUUID().toString(),
                        channel,
                        request.to(),
                        request.body(),
                        clock.instant().toString());
        outbox.add(delivered);
        return delivered;
    }

    @GetMapping("/outbox")
    public List<Delivered> outbox(@RequestParam(required = false) String channel) {
        if (channel == null) {
            return List.copyOf(outbox);
        }
        String c = channel.toLowerCase(Locale.ROOT);
        return outbox.stream().filter(d -> d.channel().equals(c)).toList();
    }

    public record SendRequest(@NotBlank String channel, String to, @NotBlank String body) {}

    public record Delivered(String id, String channel, String to, String body, String sentAt) {}
}
