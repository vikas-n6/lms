package com.lms.customer.adapters.in.web;

import com.lms.customer.domain.Customer;
import java.time.Instant;

/**
 * Response body. Deliberately omits phone and full name: the UI shows the non-PII {@code
 * displayName} (CLAUDE.md "Logging"/docs/domain-rules.md).
 */
public record CustomerResponse(String id, String displayName, String status, Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.id().toString(),
                customer.displayName(),
                customer.status().name(),
                customer.createdAt());
    }
}
