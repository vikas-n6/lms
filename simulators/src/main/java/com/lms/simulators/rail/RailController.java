package com.lms.simulators.rail;

import com.lms.simulators.rail.RailSimulator.PayoutRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface of the rail simulator (phase prompt S3). */
@RestController
@RequestMapping("/sim/rail")
public class RailController {

    private final RailSimulator simulator;

    public RailController(RailSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/payout")
    public PayoutResponse payout(@RequestBody PayoutInitiate request) {
        Payout payout =
                simulator.payout(
                        new PayoutRequest(
                                request.idempotencyKey(),
                                request.loanId(),
                                request.amountMinor(),
                                request.currency(),
                                request.callbackUrl()));
        return new PayoutResponse(payout.railRef(), payout.idempotencyKey(), "ACCEPTED");
    }

    @GetMapping("/payout")
    public ResponseEntity<Payout> status(@RequestParam String idempotencyKey) {
        return simulator
                .byKey(idempotencyKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record PayoutInitiate(
            @NotBlank String idempotencyKey,
            @NotBlank String loanId,
            long amountMinor,
            @NotBlank String currency,
            String callbackUrl) {}

    public record PayoutResponse(String railRef, String idempotencyKey, String status) {}
}
