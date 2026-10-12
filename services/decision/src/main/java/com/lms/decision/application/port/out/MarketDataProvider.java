package com.lms.decision.application.port.out;

import com.lms.decision.application.MarketData;

/**
 * Supplies the bureau/bank data for a decision. Implemented by the {@code BureauBankFanout} (which
 * runs the two calls in parallel via Fanout); a port so the orchestrator can be unit-tested without
 * the preview concurrency machinery.
 */
public interface MarketDataProvider {
    MarketData fetch(String subjectId, String phone);
}
