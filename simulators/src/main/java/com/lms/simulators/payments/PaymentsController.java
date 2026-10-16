package com.lms.simulators.payments;

import com.lms.simulators.payments.PaymentsSimulator.CreateLinkRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface of the payments simulator (phase prompt S3). */
@RestController
@RequestMapping("/sim/payments")
public class PaymentsController {

    private final PaymentsSimulator simulator;

    public PaymentsController(PaymentsSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/create-link")
    public PaymentLink createLink(@RequestBody CreateLinkBody body) {
        return simulator.createLink(
                new CreateLinkRequest(
                        body.loanId(),
                        body.amountMinor(),
                        body.currency(),
                        body.autoComplete(),
                        body.callbackUrl()));
    }

    public record CreateLinkBody(
            @NotBlank String loanId,
            long amountMinor,
            @NotBlank String currency,
            boolean autoComplete,
            String callbackUrl) {}
}
