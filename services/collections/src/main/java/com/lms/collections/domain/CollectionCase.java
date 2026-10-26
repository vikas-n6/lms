package com.lms.collections.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A collection case for a delinquent loan (phase prompt task 4). Opened at DPD 1 or more; carries
 * the current bucket/DPD, the assigned agent, any promise-to-pay, and the dunning plan version it
 * runs under. collections never reads loan tables — this state is built from the events it consumes
 * (docs/architecture.md). Mutable with optimistic locking.
 */
public record CollectionCase(
        UUID id,
        String loanId,
        String loanNumber,
        CaseStatus status,
        String bucket,
        int daysPastDue,
        String assignedAgent,
        LocalDate promiseDate,
        Long promiseAmountMinor,
        String currency,
        int planVersion,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public CollectionCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(loanNumber, "loanNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static CollectionCase open(
            UUID id,
            String loanId,
            String loanNumber,
            String bucket,
            int daysPastDue,
            int planVersion,
            Instant now) {
        return new CollectionCase(
                id,
                loanId,
                loanNumber,
                CaseStatus.OPEN,
                bucket,
                daysPastDue,
                null,
                null,
                null,
                null,
                planVersion,
                now,
                now,
                0L);
    }

    public CollectionCase withStatus(CaseStatus newStatus, Instant now) {
        return new CollectionCase(
                id,
                loanId,
                loanNumber,
                newStatus,
                bucket,
                daysPastDue,
                assignedAgent,
                promiseDate,
                promiseAmountMinor,
                currency,
                planVersion,
                createdAt,
                now,
                version);
    }

    public CollectionCase withDpd(String newBucket, int newDpd, Instant now) {
        return new CollectionCase(
                id,
                loanId,
                loanNumber,
                status,
                newBucket,
                newDpd,
                assignedAgent,
                promiseDate,
                promiseAmountMinor,
                currency,
                planVersion,
                createdAt,
                now,
                version);
    }

    public CollectionCase withAgent(String agent, Instant now) {
        return new CollectionCase(
                id,
                loanId,
                loanNumber,
                status,
                bucket,
                daysPastDue,
                agent,
                promiseDate,
                promiseAmountMinor,
                currency,
                planVersion,
                createdAt,
                now,
                version);
    }

    public CollectionCase withPromise(LocalDate date, Long amountMinor, String ccy, Instant now) {
        return new CollectionCase(
                id,
                loanId,
                loanNumber,
                CaseStatus.PROMISE_TO_PAY,
                bucket,
                daysPastDue,
                assignedAgent,
                date,
                amountMinor,
                ccy,
                planVersion,
                createdAt,
                now,
                version);
    }
}
