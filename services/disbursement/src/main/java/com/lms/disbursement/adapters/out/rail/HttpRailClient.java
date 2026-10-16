package com.lms.disbursement.adapters.out.rail;

import com.lms.disbursement.application.port.out.RailClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * HTTP adapter to the rail simulator (phase prompt S2). Resilience4j retry + circuit breaker wrap
 * the calls (CLAUDE.md: resilience on every outbound call); the rail is idempotent on the
 * idempotency key, so a retried POST never issues a second payout. A 504 means the response was
 * lost — reported as {@code responseLost} so the workflow queries status rather than re-paying.
 * Exercised in the Docker session; excluded from the coverage gate.
 */
@Component
public class HttpRailClient implements RailClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public HttpRailClient(
            RestClient.Builder restClientBuilder,
            @Value("${lms.sim.base-url:http://localhost:8190}") String simBaseUrl,
            Retry railRetry,
            CircuitBreaker railCircuitBreaker) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = simBaseUrl;
        this.retry = railRetry;
        this.circuitBreaker = railCircuitBreaker;
    }

    @Override
    public Ack initiatePayout(PayoutRequest request) {
        Supplier<Ack> call =
                () -> {
                    try {
                        PayoutResponse body =
                                restClient
                                        .post()
                                        .uri(baseUrl + "/sim/rail/payout")
                                        .body(
                                                Map.of(
                                                        "idempotencyKey", request.idempotencyKey(),
                                                        "loanId", request.loanId().toString(),
                                                        "amountMinor", request.amountMinor(),
                                                        "currency", request.currency(),
                                                        "callbackUrl", request.callbackUrl()))
                                        .retrieve()
                                        .body(PayoutResponse.class);
                        return new Ack(false, body == null ? null : body.railRef());
                    } catch (HttpServerErrorException.GatewayTimeout e) {
                        return new Ack(true, null); // response lost: do not re-pay, query status
                    }
                };
        return decorate(call).get();
    }

    @Override
    public StatusResult queryStatus(String idempotencyKey) {
        Supplier<StatusResult> call =
                () -> {
                    PayoutView view =
                            restClient
                                    .get()
                                    .uri(
                                            baseUrl
                                                    + "/sim/rail/payout?idempotencyKey="
                                                    + idempotencyKey)
                                    .retrieve()
                                    .body(PayoutView.class);
                    if (view == null || view.outcome() == null) {
                        return new StatusResult(Outcome.UNKNOWN, null, null, null);
                    }
                    Outcome outcome =
                            "SUCCESS".equals(view.outcome()) ? Outcome.SUCCESS : Outcome.FAILED;
                    return new StatusResult(
                            outcome, view.railRef(), view.railRef(), view.failureCode());
                };
        return decorate(call).get();
    }

    private <T> Supplier<T> decorate(Supplier<T> call) {
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));
    }

    private record PayoutResponse(String railRef, String idempotencyKey, String status) {}

    private record PayoutView(
            String idempotencyKey,
            String railRef,
            String loanId,
            long amountMinor,
            String currency,
            String outcome,
            String failureCode) {}
}
