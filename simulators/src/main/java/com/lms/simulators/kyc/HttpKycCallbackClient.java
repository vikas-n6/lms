package com.lms.simulators.kyc;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Posts the KYC resolution to the customer service callback URL. Exercised in the Docker session
 * (it needs a running peer); excluded from the coverage gate.
 */
@Component
public class HttpKycCallbackClient implements KycCallbackClient {

    private static final Logger log = LoggerFactory.getLogger(HttpKycCallbackClient.class);

    private final RestClient restClient = RestClient.create();

    @Override
    public void sendCallback(String callbackUrl, String subjectId, KycStatus finalStatus) {
        try {
            restClient
                    .post()
                    .uri(callbackUrl)
                    .body(Map.of("subjectId", subjectId, "status", finalStatus.name()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("KYC callback to {} failed: {}", callbackUrl, e.getMessage());
        }
    }
}
