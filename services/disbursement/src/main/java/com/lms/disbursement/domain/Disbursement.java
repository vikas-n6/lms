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
 * reports.
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

    /** A fresh disbursement in INITIATED with no attempts yet. */
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
                null,
                now,
                now,
                0);
    }

    public Disbursement withAttemptStarted(Instant now) {
        return copy(status, railRef, utr, disbursementDate, attempts + 1, failureReason, now);
    }

    public Disbursement succeeded(String railRef, String utr, LocalDate date, Instant now) {
        return copy(DisbursementStatus.SUCCEEDED, railRef, utr, date, attempts, null, now);
    }

    public Disbursement failed(String reason, Instant now) {
        return copy(
                DisbursementStatus.FAILED, railRef, utr, disbursementDate, attempts, reason, now);
    }

    public Disbursement toManualReview(Instant now) {
        return copy(
                DisbursementStatus.MANUAL_REVIEW,
                railRef,
                utr,
                disbursementDate,
                attempts,
                failureReason,
                now);
    }

    public Disbursement reinitiated(Instant now) {
        return copy(
                DisbursementStatus.INITIATED, railRef, utr, disbursementDate, attempts, null, now);
    }

    private Disbursement copy(
            DisbursementStatus newStatus,
            String newRailRef,
            String newUtr,
            LocalDate newDate,
            int newAttempts,
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
                newReason,
                createdAt,
                now,
                version);
    }
}
