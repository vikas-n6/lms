package com.lms.simulators.kyc;

/**
 * Delivers the asynchronous KYC resolution to the customer service (phase prompt S2). An interface
 * so tests substitute a fake and assert delivery without a running peer.
 */
public interface KycCallbackClient {
    void sendCallback(String callbackUrl, String subjectId, KycStatus finalStatus);
}
