package com.lms.repayment.adapters.out.http;

import com.lms.money.Money;
import com.lms.repayment.application.port.out.PaymentsClient;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** HTTP adapter to the payments simulator. Exercised in the Docker session; coverage-excluded. */
@Component
public class HttpPaymentsClient implements PaymentsClient {

    private final RestClient restClient;
    private final String baseUrl;

    public HttpPaymentsClient(
            RestClient.Builder builder,
            @Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl) {
        this.restClient = builder.build();
        this.baseUrl = baseUrl;
    }

    @Override
    public PaymentLink createLink(UUID loanId, Money amount, String callbackUrl) {
        LinkResponse response =
                restClient
                        .post()
                        .uri(baseUrl + "/sim/payments/create-link")
                        .body(
                                Map.of(
                                        "loanId", loanId.toString(),
                                        "amountMinor", amount.minorUnits(),
                                        "currency", amount.currency().getCurrencyCode(),
                                        "autoComplete", false,
                                        "callbackUrl", callbackUrl))
                        .retrieve()
                        .body(LinkResponse.class);
        return new PaymentLink(
                response == null ? null : response.linkId(),
                response == null ? null : response.url());
    }

    private record LinkResponse(
            String linkId,
            String url,
            String loanId,
            long amountMinor,
            String currency,
            String status) {}
}
