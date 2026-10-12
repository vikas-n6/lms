package com.lms.origination.application.port.out;

/**
 * Compliance gate (CLAUDE.md locked decision: a {@code CompliancePolicy} port with a permissive
 * implementation). Called before offer creation and before acceptance; each result is recorded. A
 * real market implementation would live behind this port without touching the flow.
 */
public interface CompliancePolicy {

    ComplianceResult check(ComplianceCheck check);

    /** What is being gated. */
    enum Stage {
        OFFER,
        ACCEPTANCE
    }

    /**
     * @param stage the lifecycle point being gated
     * @param applicationId the application under check
     * @param customerId the customer under check
     */
    record ComplianceCheck(Stage stage, String applicationId, String customerId) {}

    /**
     * @param allowed whether the flow may proceed
     * @param code machine-readable result code (e.g. ALLOWED)
     * @param detail human-readable explanation, recorded with the decision
     */
    record ComplianceResult(boolean allowed, String code, String detail) {
        public static ComplianceResult allow() {
            return new ComplianceResult(true, "ALLOWED", "permissive policy");
        }
    }
}
