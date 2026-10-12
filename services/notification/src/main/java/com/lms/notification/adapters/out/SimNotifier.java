package com.lms.notification.adapters.out;

import com.lms.notification.application.NotificationSink;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Logs the notification and posts it to the SMS simulator outbox (phase prompt S7 stub). */
@Component
public class SimNotifier implements NotificationSink {

    private static final Logger log = LoggerFactory.getLogger(SimNotifier.class);

    private final RestClient restClient;
    private final String opsRecipient;

    public SimNotifier(
            @Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl,
            @Value("${lms.notification.ops-recipient:ops}") String opsRecipient) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.opsRecipient = opsRecipient;
    }

    @Override
    public void deliver(String line) {
        log.info("{}", line);
        try {
            restClient
                    .post()
                    .uri("/sim/sms/send")
                    .body(Map.of("to", opsRecipient, "body", line))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("notification SMS send failed: {}", e.getMessage());
        }
    }
}
