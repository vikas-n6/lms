package com.lms.disbursement.domain;

import com.lms.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The payout aggregate. Immutable; each transition returns a new instance so the state is never
 * mutated in place. Amounts are {@link Money} (minor units + currency), never doubles/BigDecimal
 * (CLAUDE.md money rules). The net amount is supplied by loan-account via {@code loan.created}
 * (principal − fee − GST) and is what the rail pays out and what {@code disbursement.succeeded}
 * reports. {@code initiatedPublished} makes the {@code disbursement.initiated} publish idempotent
 * across the workflow's at-least-once activity execution.
 */
public record Disbursement(
        UUID id,
        UUID loanId,
        String loanNumber,
        DisbursementStatus status,
        Money principal,
        Money fee,
        Money gst,
        Money net,
        String railRef,
        String utr,
        LocalDate disbursementDate,
        int attempts,
        boolean initiatedPublished,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public Disbursement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(net, "net");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
    }

    /** A fresh disbursement in INITIATED with no attempts and nothing published yet. */
    public static Disbursement initiate(
            UUID id,
            UUID loanId,
            String loanNumber,
            Money principal,
            Money fee,
            Money gst,
            Money net,
            Instant now) {
        return new Disbursement(
                id,
                loanId,
                loanNumber,
                DisbursementStatus.INITIATED,
                principal,
                fee,
                gst,
                net,
                null,
                null,
                null,
                0,
                false,
                null,
                now,
                now,
                0);
    }

    public Disbursement withAttemptStarted(Instant now) {
        return copy(
                status,
                railRef,
                utr,
                disbursementDate,
                attempts + 1,
                initiatedPublished,
                failureReason,
                now);
    }

    public Disbursement markInitiatedPublished(Instant now) {
        return copy(status, railRef, utr, disbursementDate, attempts, true, failureReason, now);
    }

    public Disbursement succeeded(String newRailRef, String newUtr, LocalDate date, Instant now) {
        return copy(
                DisbursementStatus.SUCCEEDED,
                newRailRef,
                newUtr,
                date,
                attempts,
                initiatedPublished,
                null,
                now);
    }

    public Disbursement failed(String reason, Instant now) {
        return copy(
                DisbursementStatus.FAILED,
                railRef,
                utr,
                disbursementDate,
                attempts,
                initiatedPublished,
                reason,
                now);
    }

    public Disbursement toManualReview(Instant now) {
        return copy(
                DisbursementStatus.MANUAL_REVIEW,
                railRef,
                utr,
                disbursementDate,
                attempts,
                initiatedPublished,
                failureReason,
                now);
    }

    public Disbursement reinitiated(Instant now) {
        return copy(
                DisbursementStatus.INITIATED,
                railRef,
                utr,
                disbursementDate,
                attempts,
                initiatedPublished,
                null,
                now);
    }

    private Disbursement copy(
            DisbursementStatus newStatus,
            String newRailRef,
            String newUtr,
            LocalDate newDate,
            int newAttempts,
            boolean newInitiatedPublished,
            String newReason,
            Instant now) {
        return new Disbursement(
                id,
                loanId,
                loanNumber,
                newStatus,
                principal,
                fee,
                gst,
                net,
                newRailRef,
                newUtr,
                newDate,
                newAttempts,
                newInitiatedPublished,
                newReason,
                createdAt,
                now,
                version);
    }
}
