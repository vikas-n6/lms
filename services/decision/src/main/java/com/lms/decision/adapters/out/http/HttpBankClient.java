package com.lms.decision.adapters.out.http;

import com.lms.decision.application.port.out.BankClient;
import com.lms.money.Money;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.Currency;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the bank statement-analysis simulator over REST, wrapped in Resilience4j (CLAUDE.md). */
@Component
public class HttpBankClient implements BankClient {

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public HttpBankClient(
            @Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl,
            Retry bankRetry,
            CircuitBreaker bankCircuitBreaker) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.retry = bankRetry;
        this.circuitBreaker = bankCircuitBreaker;
    }

    @Override
    public Money estimatedMonthlyIncome(String subjectId) {
        Supplier<StatementAnalysis> call =
                () ->
                        restClient
                                .post()
                                .uri("/sim/bank/statement-analysis")
                                .body(new StatementRequest(subjectId))
                                .retrieve()
                                .body(StatementAnalysis.class);
        StatementAnalysis analysis =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call))
                        .get();
        return Money.of(
                analysis.estimatedMonthlyIncomeMinor(), Currency.getInstance(analysis.currency()));
    }

    private record StatementRequest(String subjectId) {}

    private record StatementAnalysis(long estimatedMonthlyIncomeMinor, String currency) {}
}
