package com.lms.repayment.adapters.in.web;

import com.lms.config.BusinessCalendar;
import com.lms.money.Money;
import com.lms.repayment.application.RecordRepaymentService;
import com.lms.repayment.application.ReverseRepaymentService;
import com.lms.repayment.application.port.out.RepaymentEventPublisher;
import com.lms.repayment.domain.RePresentationPolicy;
import com.lms.repayment.domain.RepaymentChannel;
import com.lms.security.callback.CallbackVerifier;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives the mandate simulator's signed debit result (phase prompt task 3): SUCCESS records
 * {@code repayment.received}; FAILED publishes {@code mandate.debit-failed} with the single
 * re-presentation date (two business days later); RETURNED reverses a prior receipt. Verifies the
 * signature before any effect.
 */
@RestController
@RequestMapping("/callbacks")
public class MandatesCallbackController {

    private static final String SIGNATURE_HEADER = "X-Callback-Signature";

    private final CallbackVerifier verifier;
    private final RecordRepaymentService recordService;
    private final ReverseRepaymentService reverseService;
    private final RepaymentEventPublisher publisher;
    private final BusinessCalendar calendar;
    private final ObjectMapper objectMapper;
    private final int rePresentBusinessDays;

    public MandatesCallbackController(
            CallbackVerifier verifier,
            RecordRepaymentService recordService,
            ReverseRepaymentService reverseService,
            RepaymentEventPublisher publisher,
            BusinessCalendar calendar,
            ObjectMapper objectMapper,
            @Value("${lms.repayment.re-present-business-days:2}") int rePresentBusinessDays) {
        this.verifier = verifier;
        this.recordService = recordService;
        this.reverseService = reverseService;
        this.publisher = publisher;
        this.calendar = calendar;
        this.objectMapper = objectMapper;
        this.rePresentBusinessDays = rePresentBusinessDays;
    }

    @PostMapping("/mandates")
    public ResponseEntity<Void> mandates(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String rawBody) {
        verifier.verify(signature, rawBody);
        DebitCallback cb = parse(rawBody);
        UUID loanId = UUID.fromString(cb.loanId());
        Money amount = Money.of(cb.amountMinor(), Currency.getInstance(cb.currency()));
        switch (cb.status()) {
            case "SUCCESS" ->
                    recordService.record(
                            loanId,
                            amount,
                            RepaymentChannel.MANDATE,
                            null,
                            null,
                            cb.idempotencyKey());
            case "RETURNED" -> reverseService.reverse(cb.idempotencyKey(), "DEBIT_RETURNED");
            case "FAILED" ->
                    publisher.mandateDebitFailed(
                            loanId,
                            cb.mandateId(),
                            amount,
                            calendar.today(),
                            cb.failureCode(),
                            RePresentationPolicy.rePresentOn(
                                    calendar.today(), rePresentBusinessDays, Set.of()));
            default -> {
                /* unknown status: ignore */
            }
        }
        return ResponseEntity.ok().build();
    }

    private DebitCallback parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, DebitCallback.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("malformed mandate callback body", e);
        }
    }

    /** Shape the mandate simulator posts back (simulators MandateSimulator.DebitCallback). */
    public record DebitCallback(
            String idempotencyKey,
            String mandateId,
            String loanId,
            long amountMinor,
            String currency,
            String status,
            String failureCode) {}
}
