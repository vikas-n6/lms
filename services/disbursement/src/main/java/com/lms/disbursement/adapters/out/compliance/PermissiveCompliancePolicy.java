package com.lms.disbursement.adapters.out.compliance;

import com.lms.disbursement.application.port.out.CompliancePolicy;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The permissive compliance implementation for this learning project: every payout passes
 * (CLAUDE.md "Mocked behind a CompliancePolicy port with a permissive implementation"). A real
 * policy replaces this behind the port without touching the saga.
 */
@Component
public class PermissiveCompliancePolicy implements CompliancePolicy {

    @Override
    public ComplianceResult check(UUID loanId) {
        return ComplianceResult.allow();
    }
}
