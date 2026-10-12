package com.lms.customer.adapters.out.http;

import com.lms.customer.application.port.out.KycClient;
import com.lms.customer.domain.KycStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the KYC simulator over REST, wrapped in Resilience4j retry + circuit breaker (CLAUDE.md).
 */
@Component
public class HttpKycClient implements KycClient {

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public HttpKycClient(
            @Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl,
            Retry kycRetry,
            CircuitBreaker kycCircuitBreaker) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.retry = kycRetry;
        this.circuitBreaker = kycCircuitBreaker;
    }

    @Override
    public KycStatus verify(String subjectId, String callbackUrl) {
        Supplier<VerifyResponse> call =
                () ->
                        restClient
                                .post()
                                .uri("/sim/kyc/verify")
                                .body(new VerifyRequest(subjectId, callbackUrl))
                                .retrieve()
                                .body(VerifyResponse.class);
        VerifyResponse response =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call))
                        .get();
        return KycStatus.valueOf(response.status());
    }

    private record VerifyRequest(String subjectId, String callbackUrl) {}

    private record VerifyResponse(String status) {}
}
