package com.lms.decision.adapters.out.http;

import com.lms.decision.application.port.out.BureauClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the bureau simulator over REST, wrapped in a Resilience4j circuit breaker + retry
 * (CLAUDE.md "Resilience4j on every outbound call"). After retries are exhausted it throws, which
 * the fan-out turns into "bureau unavailable" → REFER. Verified against the running simulator in
 * the Docker session.
 */
@Component
public class HttpBureauClient implements BureauClient {

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public HttpBureauClient(
            @Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl,
            Retry bureauRetry,
            CircuitBreaker bureauCircuitBreaker) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.retry = bureauRetry;
        this.circuitBreaker = bureauCircuitBreaker;
    }

    @Override
    public BureauReport pull(String subjectId, String phone) {
        Supplier<BureauReport> call =
                () ->
                        restClient
                                .post()
                                .uri("/sim/bureau/pull")
                                .body(new PullRequest(subjectId, phone))
                                .retrieve()
                                .body(BureauReport.class);
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call))
                .get();
    }

    private record PullRequest(String subjectId, String phone) {}
}
