package com.lms.disbursement.application.port.out;

import java.util.UUID;

/**
 * Compliance gate before a payout (CLAUDE.md locked decision: a {@code CompliancePolicy} port with
 * a permissive implementation). A real market policy would live behind this port without touching
 * the saga.
 */
public interface CompliancePolicy {

    ComplianceResult check(UUID loanId);

    record ComplianceResult(boolean allowed, String code, String detail) {
        public static ComplianceResult allow() {
            return new ComplianceResult(true, "ALLOWED", "permissive policy");
        }
    }
}
