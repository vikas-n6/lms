package com.lms.notification.adapters.out.channel;

import com.lms.notification.application.port.out.ChannelGateway;
import com.lms.notification.domain.Channel;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Delivers a message through the channel simulator (phase prompt task 5). Each channel has its own
 * failure settings in the simulator; a non-2xx or transport error becomes a {@link
 * ChannelDeliveryException} so the service retries then fails. Live delivery is exercised in the
 * Docker session.
 */
@Component
public class HttpChannelGateway implements ChannelGateway {

    private final RestClient restClient;

    public HttpChannelGateway(@Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void send(Channel channel, String recipientRef, String body) {
        try {
            restClient
                    .post()
                    .uri("/sim/notify")
                    .body(
                            Map.of(
                                    "channel",
                                    channel.name(),
                                    "to",
                                    recipientRef == null ? "unknown" : recipientRef,
                                    "body",
                                    body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ChannelDeliveryException("CHANNEL_UNAVAILABLE", e.getMessage());
        }
    }
}
