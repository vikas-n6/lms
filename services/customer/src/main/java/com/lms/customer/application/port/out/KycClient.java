package com.lms.customer.application.port.out;

import com.lms.customer.domain.KycStatus;

/** Outbound port to the KYC simulator (behind Resilience4j). PENDING resolves via a callback. */
public interface KycClient {
    KycStatus verify(String subjectId, String callbackUrl);
}
