package com.lms.loanaccount.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A two-person write-off request (phase prompt task 2, docs/domain-rules.md "Loan state machine").
 * Raised by one admin with a mandatory reason; approved only by a <em>different</em> admin (the
 * same user cannot approve their own request — enforced here) or rejected. The DPD-at-least-180
 * guard is checked by the application layer against versioned policy, so no threshold is hard-coded
 * in the domain (CLAUDE.md global do-not list). Immutable; a decision returns a new instance.
 */
public record WriteOffRequest(
        UUID id,
        UUID loanId,
        String loanNumber,
        String reason,
        String requestedBy,
        WriteOffRequestStatus status,
        String decidedBy,
        String decisionNote,
        int dpdAtRequest,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public WriteOffRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(loanNumber, "loanNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a write-off reason is required");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("requestedBy is required");
        }
    }

    /** A freshly raised request in {@link WriteOffRequestStatus#REQUESTED}. */
    public static WriteOffRequest raise(
            UUID id,
            UUID loanId,
            String loanNumber,
            String reason,
            String requestedBy,
            int dpdAtRequest,
            Instant now) {
        return new WriteOffRequest(
                id,
                loanId,
                loanNumber,
                reason,
                requestedBy,
                WriteOffRequestStatus.REQUESTED,
                null,
                null,
                dpdAtRequest,
                now,
                now,
                0L);
    }

    /**
     * Approve this request. Guards: it must still be {@code REQUESTED}, and {@code approver} must
     * differ from {@link #requestedBy} (the two-person rule — phase prompt "Do not").
     */
    public WriteOffRequest approve(String approver, Instant now) {
        requirePending();
        if (approver == null || approver.isBlank()) {
            throw new IllegalArgumentException("approver is required");
        }
        if (approver.equals(requestedBy)) {
            throw new SelfApprovalException(id, requestedBy);
        }
        return decided(WriteOffRequestStatus.APPROVED, approver, null, now);
    }

    /** Reject this request (any admin, including the requester). */
    public WriteOffRequest reject(String decidedBy, String note, Instant now) {
        requirePending();
        if (decidedBy == null || decidedBy.isBlank()) {
            throw new IllegalArgumentException("decidedBy is required");
        }
        return decided(WriteOffRequestStatus.REJECTED, decidedBy, note, now);
    }

    private void requirePending() {
        if (status != WriteOffRequestStatus.REQUESTED) {
            throw new IllegalStateException("write-off request " + id + " is already " + status);
        }
    }

    private WriteOffRequest decided(
            WriteOffRequestStatus newStatus, String by, String note, Instant now) {
        return new WriteOffRequest(
                id,
                loanId,
                loanNumber,
                reason,
                requestedBy,
                newStatus,
                by,
                note,
                dpdAtRequest,
                createdAt,
                now,
                version);
    }

    /** Raised when an admin tries to approve their own write-off request (two-person rule). */
    public static final class SelfApprovalException extends RuntimeException {
        public SelfApprovalException(UUID requestId, String user) {
            super("user " + user + " cannot approve their own write-off request " + requestId);
        }
    }
}
