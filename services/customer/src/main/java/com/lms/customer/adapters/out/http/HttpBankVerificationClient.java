package com.lms.customer.adapters.out.http;

import com.lms.customer.application.port.out.BankVerificationClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the bank penny-drop simulator over REST, wrapped in Resilience4j (CLAUDE.md). */
@Component
public class HttpBankVerificationClient implements BankVerificationClient {

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public HttpBankVerificationClient(
            @Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl,
            Retry bankRetry,
            CircuitBreaker bankCircuitBreaker) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.retry = bankRetry;
        this.circuitBreaker = bankCircuitBreaker;
    }

    @Override
    public PennyDrop pennyDrop(String accountNumber, String ifsc) {
        Supplier<PennyDropResponse> call =
                () ->
                        restClient
                                .post()
                                .uri("/sim/bank/penny-drop")
                                .body(new PennyDropRequest(accountNumber, ifsc))
                                .retrieve()
                                .body(PennyDropResponse.class);
        PennyDropResponse response =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call))
                        .get();
        return new PennyDrop(response.verified(), response.maskedAccountNumber());
    }

    private record PennyDropRequest(String accountNumber, String ifsc) {}

    private record PennyDropResponse(boolean verified, String maskedAccountNumber, String ifsc) {}
}
