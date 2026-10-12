package com.lms.decision.application.port.out;

/**
 * Outbound port to the credit-bureau simulator (through the gateway/simulator, never another
 * service's DB). The implementation wraps the HTTP call in Resilience4j retry/circuit-breaker;
 * after retries are exhausted it throws, and the fan-out treats that as "bureau unavailable".
 */
public interface BureauClient {

    BureauReport pull(String subjectId, String phone);

    /**
     * @param score 300..900
     * @param activeAccounts active credit accounts
     * @param overdue true when an active overdue account is reported
     */
    record BureauReport(int score, int activeAccounts, boolean overdue) {}
}
