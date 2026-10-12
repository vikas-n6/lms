package com.lms.origination.adapters.out.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.origination.application.port.out.CompliancePolicy.ComplianceCheck;
import com.lms.origination.application.port.out.CompliancePolicy.Stage;
import org.junit.jupiter.api.Test;

class PermissiveCompliancePolicyTest {

    private final PermissiveCompliancePolicy policy = new PermissiveCompliancePolicy();

    @Test
    void allowsEveryStage() {
        assertThat(policy.check(new ComplianceCheck(Stage.OFFER, "app-1", "cust-1")).allowed())
                .isTrue();
        assertThat(policy.check(new ComplianceCheck(Stage.ACCEPTANCE, "app-1", "cust-1")).code())
                .isEqualTo("ALLOWED");
    }
}
