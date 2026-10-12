package com.lms.simulators.kyc;

/** KYC outcome (phase prompt S2). PENDING is resolved later via an asynchronous callback. */
public enum KycStatus {
    VERIFIED,
    FAILED,
    PENDING
}
