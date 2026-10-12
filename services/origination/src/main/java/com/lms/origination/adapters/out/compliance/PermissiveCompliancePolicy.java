package com.lms.origination.adapters.out.compliance;

import com.lms.origination.application.port.out.CompliancePolicy;
import org.springframework.stereotype.Component;

/**
 * The permissive compliance implementation for this learning project: every check passes (CLAUDE.md
 * "Mocked behind a CompliancePolicy port with a permissive implementation"). The result is still
 * recorded by the caller so the audit trail exists when a real policy replaces this.
 */
@Component
public class PermissiveCompliancePolicy implements CompliancePolicy {

    @Override
    public ComplianceResult check(ComplianceCheck check) {
        return ComplianceResult.allow();
    }
}
