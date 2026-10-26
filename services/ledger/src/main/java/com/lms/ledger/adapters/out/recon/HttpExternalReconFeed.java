package com.lms.ledger.adapters.out.recon;

import com.lms.ledger.application.port.out.ExternalReconFeed;
import com.lms.ledger.domain.recon.ReconLine;
import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the bank statement and rail settlement report for a date from the simulators (phase prompt
 * task 6). A {@code reconDriftRate} knob on the simulators injects discrepancies. Exercised against
 * the running simulators in the Docker session.
 */
@Component
public class HttpExternalReconFeed implements ExternalReconFeed {

    private final RestClient restClient;
    private final Currency currency;

    public HttpExternalReconFeed(
            @Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl,
            @Value("${lms.currency.default:INR}") String currencyCode) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.currency = Currency.getInstance(currencyCode);
    }

    @Override
    public List<ReconLine> bankStatement(LocalDate date) {
        return fetch("/sim/bank/statement", date);
    }

    @Override
    public List<ReconLine> railReport(LocalDate date) {
        return fetch("/sim/rail/settlement-report", date);
    }

    private List<ReconLine> fetch(String path, LocalDate date) {
        List<StatementLine> lines =
                restClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path(path).queryParam("date", date).build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<StatementLine>>() {});
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(l -> new ReconLine(l.reference(), Money.of(l.amountMinor(), currency)))
                .toList();
    }

    /** A statement / report line as returned by the simulators. */
    public record StatementLine(String reference, long amountMinor) {}
}
