package com.lms.simulators.mandate;

import com.lms.simulators.mandate.MandateSimulator.DebitRequest;
import com.lms.simulators.mandate.MandateSimulator.DebitResult;
import com.lms.simulators.mandate.MandateSimulator.RegisterRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface of the mandate simulator (phase prompt S3). */
@RestController
@RequestMapping("/sim/mandate")
public class MandateController {

    private final MandateSimulator simulator;

    public MandateController(MandateSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/register")
    public Mandate register(@RequestBody RegisterBody body) {
        return simulator.register(
                new RegisterRequest(body.loanId(), body.customerId(), body.callbackUrl()));
    }

    @PostMapping("/debit")
    public DebitResult debit(@RequestBody DebitBody body) {
        return simulator.debit(
                new DebitRequest(
                        body.idempotencyKey(),
                        body.mandateId(),
                        body.loanId(),
                        body.amountMinor(),
                        body.currency(),
                        body.callbackUrl()));
    }

    @DeleteMapping("/{mandateId}")
    public ResponseEntity<Map<String, String>> cancel(@PathVariable String mandateId) {
        return simulator.cancel(mandateId)
                ? ResponseEntity.ok(Map.of("mandateId", mandateId, "status", Mandate.CANCELLED))
                : ResponseEntity.notFound().build();
    }

    public record RegisterBody(
            @NotBlank String loanId, @NotBlank String customerId, String callbackUrl) {}

    public record DebitBody(
            @NotBlank String idempotencyKey,
            @NotBlank String mandateId,
            @NotBlank String loanId,
            long amountMinor,
            @NotBlank String currency,
            String callbackUrl) {}
}
