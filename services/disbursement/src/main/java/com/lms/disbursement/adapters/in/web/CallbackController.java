package com.lms.disbursement.adapters.in.web;

import com.lms.disbursement.application.CallbackClassifier;
import com.lms.disbursement.application.port.out.WorkflowStarter;
import com.lms.disbursement.domain.RailStatus;
import com.lms.security.callback.CallbackVerifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives the rail's signed asynchronous callback (phase prompt S2). It verifies the signature
 * before any effect (callbacks bypass JWT but must be signed), de-duplicates on {@code (railRef,
 * status)} and resolves against the persisted state: a first terminal result signals the workflow,
 * a duplicate is a 200 no-op, and a conflicting terminal result is ignored, logged and counted. It
 * acknowledges quickly — the heavy work is the workflow's.
 */
@RestController
@RequestMapping("/callbacks")
public class CallbackController {

    private static final Logger log = LoggerFactory.getLogger(CallbackController.class);
    private static final String SIGNATURE_HEADER = "X-Callback-Signature";

    private final CallbackVerifier verifier;
    private final CallbackClassifier classifier;
    private final WorkflowStarter workflows;
    private final ObjectMapper objectMapper;
    private final Counter conflictCounter;

    public CallbackController(
            CallbackVerifier verifier,
            CallbackClassifier classifier,
            WorkflowStarter workflows,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.verifier = verifier;
        this.classifier = classifier;
        this.workflows = workflows;
        this.objectMapper = objectMapper;
        this.conflictCounter = meterRegistry.counter("lms.disbursement.callback.conflicts");
    }

    @PostMapping("/rail")
    public ResponseEntity<Void> rail(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String rawBody) {
        verifier.verify(signature, rawBody); // 401 on an invalid/missing signature
        RailCallbackPayload payload = parse(rawBody);

        RailStatus status = toStatus(payload.status());
        if (status == null) {
            log.warn("ignoring rail callback with unknown status {}", payload.status());
            return ResponseEntity.ok().build();
        }
        UUID loanId = UUID.fromString(payload.loanId());
        CallbackClassifier.Result result = classifier.classify(loanId, payload.railRef(), status);
        switch (result) {
            case APPLIED ->
                    workflows.signalRailResult(
                            loanId,
                            new WorkflowStarter.RailSignal(
                                    status == RailStatus.SUCCESS,
                                    payload.railRef(),
                                    payload.utr(),
                                    payload.failureCode()));
            case CONFLICT -> {
                conflictCounter.increment();
                log.warn(
                        "conflicting rail callback {} for loan {} ignored (already terminal)",
                        status,
                        loanId);
            }
            case DUPLICATE -> log.debug("duplicate rail callback for loan {} ignored", loanId);
            case UNKNOWN_DISBURSEMENT ->
                    log.warn("rail callback for unknown disbursement on loan {}", loanId);
        }
        return ResponseEntity.ok().build();
    }

    private RailCallbackPayload parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, RailCallbackPayload.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("malformed rail callback body", e);
        }
    }

    private static RailStatus toStatus(String raw) {
        if ("SUCCESS".equals(raw)) {
            return RailStatus.SUCCESS;
        }
        if ("FAILED".equals(raw)) {
            return RailStatus.FAILED;
        }
        return null;
    }

    /** Shape the rail simulator posts back (see simulators RailSimulator.RailCallback). */
    public record RailCallbackPayload(
            String railRef,
            String idempotencyKey,
            String loanId,
            String status,
            String utr,
            String failureCode) {}
}
