package com.lms.security.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.security.pii.Pii;
import org.junit.jupiter.api.Test;

class AuditSummaryTest {

    @Test
    void rendersInsertionOrderAsStableKeyValues() {
        String rendered =
                AuditSummary.create()
                        .with("version", 2)
                        .with("apr", "24.00")
                        .with("principalMax", 50000)
                        .render();
        assertThat(rendered).isEqualTo("version=2; apr=24.00; principalMax=50000");
    }

    @Test
    void emptySummaryRendersAsNullSoThePayloadStaysNull() {
        assertThat(AuditSummary.create().render()).isNull();
        assertThat(AuditSummary.create().isEmpty()).isTrue();
    }

    @Test
    void stateTransitionHelperReadsFromTo() {
        String rendered = AuditSummary.transition("REQUESTED", "APPROVED").render();
        assertThat(rendered).isEqualTo("state=REQUESTED->APPROVED");
    }

    @Test
    void sensitiveValuesAreMaskedNotStoredRaw() {
        // A summary must never carry raw PII (CLAUDE.md "Logging"); masked values go through Pii.
        String rendered =
                AuditSummary.create()
                        .withMasked("account", "123456789012", Pii::maskAccount)
                        .render();
        assertThat(rendered).isEqualTo("account=********9012");
        assertThat(rendered).doesNotContain("12345678");
    }

    @Test
    void nullValuesAreRenderedAsTheWordNull() {
        assertThat(AuditSummary.create().with("foreclosureCharge", null).render())
                .isEqualTo("foreclosureCharge=null");
    }
}
