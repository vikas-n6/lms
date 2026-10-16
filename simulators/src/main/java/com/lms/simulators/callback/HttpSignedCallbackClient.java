package com.lms.simulators.callback;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Posts a signed callback to the peer URL. The body is serialised once to the exact bytes that are
 * signed and sent, so the receiver verifies the same string it parses. Exercised in the Docker
 * session (it needs a running peer); excluded from the coverage gate like {@code
 * HttpKycCallbackClient}.
 */
@Component
public class HttpSignedCallbackClient implements SignedCallbackClient {

    private static final Logger log = LoggerFactory.getLogger(HttpSignedCallbackClient.class);

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private final SimCallbackSigner signer;
    private final Clock clock;

    public HttpSignedCallbackClient(
            ObjectMapper objectMapper, SimCallbackSigner signer, Clock clock) {
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.clock = clock;
    }

    @Override
    public void deliver(String callbackUrl, Object body) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }
        String raw;
        try {
            raw = objectMapper.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("could not serialise callback body", e);
        }
        String signature = signer.sign(clock.instant(), raw);
        try {
            restClient
                    .post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SimCallbackSigner.HEADER_NAME, signature)
                    .body(raw)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("signed callback to {} failed: {}", callbackUrl, e.getMessage());
        }
    }
}
