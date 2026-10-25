package com.lms.repayment.adapters.out.http;

import com.lms.money.Money;
import com.lms.repayment.application.port.out.MandateClient;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** HTTP adapter to the mandate simulator. Exercised in the Docker session; coverage-excluded. */
@Component
public class HttpMandateClient implements MandateClient {

    private final RestClient restClient;
    private final String baseUrl;

    public HttpMandateClient(
            RestClient.Builder builder,
            @Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl) {
        this.restClient = builder.build();
        this.baseUrl = baseUrl;
    }

    @Override
    public String register(UUID loanId, String customerId, String callbackUrl) {
        RegisterResponse response =
                restClient
                        .post()
                        .uri(baseUrl + "/sim/mandate/register")
                        .body(
                                Map.of(
                                        "loanId", loanId.toString(),
                                        "customerId", customerId,
                                        "callbackUrl", callbackUrl))
                        .retrieve()
                        .body(RegisterResponse.class);
        return response == null ? null : response.mandateId();
    }

    @Override
    public void debit(
            String providerMandateId,
            UUID loanId,
            Money amount,
            String idempotencyKey,
            String callbackUrl) {
        restClient
                .post()
                .uri(baseUrl + "/sim/mandate/debit")
                .body(
                        Map.of(
                                "idempotencyKey",
                                idempotencyKey,
                                "mandateId",
                                providerMandateId,
                                "loanId",
                                loanId.toString(),
                                "amountMinor",
                                amount.minorUnits(),
                                "currency",
                                amount.currency().getCurrencyCode(),
                                "callbackUrl",
                                callbackUrl))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void cancel(String providerMandateId) {
        restClient
                .delete()
                .uri(baseUrl + "/sim/mandate/" + providerMandateId)
                .retrieve()
                .toBodilessEntity();
    }

    private record RegisterResponse(
            String mandateId, String loanId, String customerId, String status) {}
}
