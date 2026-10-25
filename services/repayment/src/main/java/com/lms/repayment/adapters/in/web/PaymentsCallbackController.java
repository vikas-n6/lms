package com.lms.repayment.adapters.in.web;

import com.lms.money.Money;
import com.lms.repayment.application.RecordRepaymentService;
import com.lms.repayment.domain.RepaymentChannel;
import com.lms.security.callback.CallbackVerifier;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives the payments simulator's signed callback (phase prompt task 3): verifies the signature
 * before any effect, then records confirmed funds as {@code repayment.received} (idempotent on the
 * link id + status).
 */
@RestController
@RequestMapping("/callbacks")
public class PaymentsCallbackController {

    private static final String SIGNATURE_HEADER = "X-Callback-Signature";

    private final CallbackVerifier verifier;
    private final RecordRepaymentService recordService;
    private final ObjectMapper objectMapper;

    public PaymentsCallbackController(
            CallbackVerifier verifier,
            RecordRepaymentService recordService,
            ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.recordService = recordService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/payments")
    public ResponseEntity<Void> payments(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String rawBody) {
        verifier.verify(signature, rawBody);
        PaymentCallback callback = parse(rawBody);
        if ("SUCCESS".equals(callback.status())) {
            Currency currency = Currency.getInstance(callback.currency());
            recordService.record(
                    UUID.fromString(callback.loanId()),
                    Money.of(callback.amountMinor(), currency),
                    RepaymentChannel.PAYMENT_LINK,
                    null,
                    null,
                    callback.linkId());
        }
        return ResponseEntity.ok().build();
    }

    private PaymentCallback parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaymentCallback.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("malformed payments callback body", e);
        }
    }

    /** Shape the payments simulator posts back (simulators PaymentsSimulator.PaymentCallback). */
    public record PaymentCallback(
            String linkId, String loanId, long amountMinor, String currency, String status) {}
}
