package com.lms.disbursement.adapters.out.temporal;

import com.lms.disbursement.application.DisbursementService;
import com.lms.disbursement.application.port.out.RailClient;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring-backed implementation of the workflow's activities: it delegates to {@link
 * DisbursementService} (which owns the ports) and maps the application rail types to the workflow's
 * {@link RailResult}. Each DB-touching activity runs in a transaction so the aggregate change and
 * its outbox row commit together. Registered with the Temporal worker in {@code TemporalConfig};
 * exercised in the Docker session.
 */
@Component
public class DisbursementActivitiesImpl implements DisbursementActivities {

    private final DisbursementService service;

    public DisbursementActivitiesImpl(DisbursementService service) {
        this.service = service;
    }

    @Override
    @Transactional
    public boolean complianceApproved(String disbursementId, String loanId) {
        return service.complianceApproved(UUID.fromString(disbursementId));
    }

    @Override
    @Transactional
    public void recordInitiated(String disbursementId) {
        service.recordInitiated(UUID.fromString(disbursementId));
    }

    @Override
    @Transactional
    public String startAttempt(String disbursementId, int attemptNumber) {
        return service.startAttempt(UUID.fromString(disbursementId), attemptNumber);
    }

    @Override
    public RailResult callRail(String disbursementId, String idempotencyKey) {
        RailClient.Ack ack = service.callRail(UUID.fromString(disbursementId), idempotencyKey);
        return ack.responseLost() ? RailResult.unknown() : RailResult.pending(ack.railRef());
    }

    @Override
    public RailResult queryStatus(String idempotencyKey) {
        RailClient.StatusResult status = service.queryStatus(idempotencyKey);
        return switch (status.outcome()) {
            case SUCCESS -> RailResult.success(status.railRef(), status.utr());
            case FAILED -> RailResult.failed(status.railRef(), status.failureCode());
            case UNKNOWN -> RailResult.unknown();
        };
    }

    @Override
    @Transactional
    public void finishAttempt(
            String disbursementId, int attemptNumber, String outcome, String failureCode) {
        service.finishAttempt(UUID.fromString(disbursementId), attemptNumber, outcome, failureCode);
    }

    @Override
    @Transactional
    public void recordSucceeded(String disbursementId, String railRef, String utr) {
        service.recordSucceeded(UUID.fromString(disbursementId), railRef, utr);
    }

    @Override
    @Transactional
    public void recordFailed(String disbursementId, String reason) {
        service.recordFailed(UUID.fromString(disbursementId), reason);
    }

    @Override
    @Transactional
    public void recordManualReview(String disbursementId) {
        service.recordManualReview(UUID.fromString(disbursementId));
    }
}
