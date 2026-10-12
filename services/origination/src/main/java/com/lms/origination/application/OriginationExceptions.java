package com.lms.origination.application;

import java.util.List;

/** Application-layer failures mapped to problem details by the web adapter. */
public final class OriginationExceptions {

    private OriginationExceptions() {}

    /** The application id was not found. */
    public static final class NotFound extends RuntimeException {
        public NotFound(String id) {
            super("application not found: " + id);
        }
    }

    /** Submission preconditions were not met (KYC/bank/consents). */
    public static final class SubmissionBlocked extends RuntimeException {
        private final List<String> missing;

        public SubmissionBlocked(List<String> missing) {
            super("submission blocked: " + missing);
            this.missing = List.copyOf(missing);
        }

        public List<String> missing() {
            return missing;
        }
    }

    /** Offer acceptance was refused (expired, tenor not offered, or bad OTP). */
    public static final class AcceptanceRefused extends RuntimeException {
        public AcceptanceRefused(String reason) {
            super("acceptance refused: " + reason);
        }
    }

    /** Compliance declined the operation. */
    public static final class ComplianceDeclined extends RuntimeException {
        public ComplianceDeclined(String detail) {
            super("compliance declined: " + detail);
        }
    }
}
