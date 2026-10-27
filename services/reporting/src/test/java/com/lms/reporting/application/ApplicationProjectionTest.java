package com.lms.reporting.application;

import static com.lms.reporting.application.TestEvents.meta;
import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.application.ApplicationOffered;
import com.lms.contracts.application.ApplicationSubmitted;
import com.lms.contracts.decision.DecisionCompleted;
import com.lms.reporting.domain.ApplicationFunnel;
import com.lms.reporting.domain.ApplicationFunnelRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationProjectionTest {

    private ReportingFakes.InMemoryApplications apps;
    private ApplicationProjection projection;

    @BeforeEach
    void setUp() {
        apps = new ReportingFakes.InMemoryApplications();
        projection = new ApplicationProjection(apps);
    }

    @Test
    void stagesAccumulateAndAreIdempotentOnRedelivery() {
        ApplicationSubmitted submitted =
                ApplicationSubmitted.newBuilder()
                        .setMetadata(meta("application.submitted", "APP-1"))
                        .setApplicationId("APP-1")
                        .setCustomerId("CUST-1")
                        .setProductId("MICRO")
                        .setRequestedAmount(TestEvents.money(50_000_00))
                        .setSnapshot(TestEvents.snapshot())
                        .setSubmittedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"))
                        .build();
        projection.onSubmitted(submitted);
        projection.onSubmitted(
                submitted); // duplicate must not create a second row or flip flags off

        DecisionCompleted approved =
                DecisionCompleted.newBuilder()
                        .setMetadata(meta("decision.completed", "APP-1"))
                        .setDecisionId("DEC-1")
                        .setApplicationId("APP-1")
                        .setCustomerId("CUST-1")
                        .setOutcome("APPROVE")
                        .setReasonCodes(List.of())
                        .setRuleSetVersion("1")
                        .setOfferedLimit(TestEvents.money(50_000_00))
                        .setBureauScore(760)
                        .setManualOverride(false)
                        .setDecidedAt(java.time.Instant.parse("2026-01-02T00:00:00Z"))
                        .build();
        projection.onDecisionCompleted(approved);

        projection.onOffered(
                ApplicationOffered.newBuilder()
                        .setMetadata(meta("application.offered", "APP-1"))
                        .setApplicationId("APP-1")
                        .setCustomerId("CUST-1")
                        .setPrincipal(TestEvents.money(50_000_00))
                        .setOptions(List.of())
                        .setOfferExpiresAt(java.time.Instant.parse("2026-01-10T00:00:00Z"))
                        .setOfferedAt(java.time.Instant.parse("2026-01-02T00:00:00Z"))
                        .build());

        ApplicationFunnelRow row = apps.rows.get("APP-1");
        assertThat(row.submitted()).isTrue();
        assertThat(row.approved()).isTrue();
        assertThat(row.offered()).isTrue();
        assertThat(row.accepted()).isFalse();

        ApplicationFunnel funnel = ApplicationFunnel.from(apps.findAll());
        assertThat(funnel.submitted()).isEqualTo(1);
        assertThat(funnel.approved()).isEqualTo(1);
        assertThat(funnel.offered()).isEqualTo(1);
    }

    @Test
    void referOutcomeLeavesApplicationPending() {
        projection.onDecisionCompleted(
                DecisionCompleted.newBuilder()
                        .setMetadata(meta("decision.completed", "APP-2"))
                        .setDecisionId("DEC-2")
                        .setApplicationId("APP-2")
                        .setCustomerId("CUST-2")
                        .setOutcome("REFER")
                        .setReasonCodes(List.of("DATA_MISMATCH"))
                        .setRuleSetVersion("1")
                        .setOfferedLimit(TestEvents.money(0))
                        .setBureauScore(640)
                        .setManualOverride(false)
                        .setDecidedAt(java.time.Instant.parse("2026-01-02T00:00:00Z"))
                        .build());

        ApplicationFunnelRow row = apps.rows.get("APP-2");
        assertThat(row.approved()).isFalse();
        assertThat(row.rejected()).isFalse();
    }
}
