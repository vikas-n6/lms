package com.lms.repayment.adapters.out.http;

import com.lms.money.Money;
import com.lms.repayment.application.port.out.DueInstalmentsPort;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads due instalments from loan-account through the gateway (gateway-mediated REST, never the
 * loan database — docs/architecture.md). Exercised in the Docker session; coverage-excluded.
 */
@Component
public class GatewayDueInstalmentsPort implements DueInstalmentsPort {

    private final RestClient restClient;
    private final String gatewayBaseUrl;

    public GatewayDueInstalmentsPort(
            RestClient.Builder builder,
            @Value("${lms.gateway.base-url:http://localhost:8080}") String gatewayBaseUrl) {
        this.restClient = builder.build();
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    @Override
    public List<DueInstalment> dueOn(LocalDate businessDate) {
        DueRow[] rows =
                restClient
                        .get()
                        .uri(gatewayBaseUrl + "/loans/due?date=" + businessDate)
                        .retrieve()
                        .body(DueRow[].class);
        if (rows == null) {
            return List.of();
        }
        return List.of(rows).stream()
                .map(
                        r ->
                                new DueInstalment(
                                        UUID.fromString(r.loanId()),
                                        LocalDate.parse(r.dueDate()),
                                        Money.of(
                                                r.amountMinor(),
                                                Currency.getInstance(r.currency()))))
                .toList();
    }

    private record DueRow(String loanId, String dueDate, long amountMinor, String currency) {}
}
