package com.lms.reporting.application;

import static com.lms.reporting.application.TestEvents.meta;
import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.audit.AuditEvent;
import com.lms.contracts.collections.CollectionsCaseClosed;
import com.lms.contracts.collections.CollectionsCaseOpened;
import com.lms.contracts.ledger.LedgerReconCompleted;
import com.lms.money.Money;
import com.lms.reporting.domain.CollectionEfficiency;
import com.lms.reporting.domain.DailySnapshot;
import com.lms.reporting.domain.DpdBucketCategory;
import com.lms.reporting.domain.LoanSnapshotRow;
import com.lms.reporting.domain.PortfolioMetrics;
import com.lms.reporting.domain.ReconStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportingReadSideTest {

    private static final Currency INR = Currency.getInstance("INR");

    private ReportingFakes.InMemoryLoans loans;
    private ReportingFakes.InMemoryCounters counters;
    private ReportingFakes.InMemoryApplications apps;
    private ReportingFakes.InMemoryAuditLog auditLog;
    private ReportingFakes.InMemoryReconStatus reconStatus;
    private ReportingFakes.InMemoryCases cases;
    private ReportingFakes.InMemorySnapshots snapshots;
    private ReportingQueryService queries;

    @BeforeEach
    void setUp() {
        loans = new ReportingFakes.InMemoryLoans();
        counters = new ReportingFakes.InMemoryCounters();
        apps = new ReportingFakes.InMemoryApplications();
        auditLog = new ReportingFakes.InMemoryAuditLog();
        reconStatus = new ReportingFakes.InMemoryReconStatus();
        cases = new ReportingFakes.InMemoryCases();
        snapshots = new ReportingFakes.InMemorySnapshots();
        queries =
                new ReportingQueryService(loans, apps, counters, auditLog, reconStatus, cases, INR);
    }

    private void seedLoan(String state, int dpd, long principal, long disbursed, String month) {
        loans.save(
                new LoanSnapshotRow(
                        UUID.randomUUID(),
                        "c",
                        1,
                        state,
                        dpd,
                        DpdBucketCategory.fromDpd(dpd),
                        Money.of(principal, INR),
                        Money.zero(INR),
                        Money.zero(INR),
                        Money.of(disbursed, INR),
                        month),
                null);
    }

    @Test
    void portfolioAndCollectionEfficiencyComputeFromReadModels() {
        seedLoan("Active", 0, 30_000_00, 30_000_00, "2026-01");
        seedLoan("Delinquent", 120, 20_000_00, 20_000_00, "2026-01");
        counters.addBilled(Money.of(10_000_00, INR));
        counters.addCollected(Money.of(8_000_00, INR));

        PortfolioMetrics p = queries.portfolio();
        assertThat(p.totalLoans()).isEqualTo(2);
        assertThat(p.outstandingPrincipal()).isEqualTo(Money.of(50_000_00, INR));
        assertThat(p.npaRatioBps()).isEqualTo(4_000L); // 20k / 50k

        CollectionEfficiency e = queries.collectionEfficiency();
        assertThat(e.efficiencyBps()).isEqualTo(8_000L);

        assertThat(queries.funnel().submitted()).isZero();
        assertThat(queries.vintage().points()).hasSize(1);
    }

    @Test
    void auditProjectionStoresAndQueryIsFilteredAndBounded() {
        AuditProjection projection = new AuditProjection(auditLog);
        projection.onAuditEvent(auditEvent("admin-1", "product.version.created"));
        projection.onAuditEvent(auditEvent("admin-2", "loan.write-off.approved"));

        assertThat(
                        queries.audit(
                                new com.lms.reporting.domain.AuditQuery(
                                        null, null, null, null, null, null, 100)))
                .hasSize(2);
        assertThat(
                        queries.audit(
                                new com.lms.reporting.domain.AuditQuery(
                                        "admin-1", null, null, null, null, null, 100)))
                .singleElement()
                .satisfies(r -> assertThat(r.action()).isEqualTo("product.version.created"));
        // A limit of 0 is replaced with the service's max, not treated as "return none".
        assertThat(
                        queries.audit(
                                new com.lms.reporting.domain.AuditQuery(
                                        null, null, null, null, null, null, 0)))
                .hasSize(2);
    }

    @Test
    void reconProjectionStoresLatestByType() {
        ReconStatusProjection projection = new ReconStatusProjection(reconStatus);
        projection.onReconCompleted(
                recon("TRIAL_BALANCE", 0, true, Instant.parse("2026-05-01T00:00:00Z")));
        projection.onReconCompleted(
                recon("TRIAL_BALANCE", 5, false, Instant.parse("2026-05-02T00:00:00Z")));

        ReconStatus latest = queries.latestRecon("TRIAL_BALANCE").orElseThrow();
        assertThat(latest.diffMinor()).isEqualTo(5L);
        assertThat(latest.balanced()).isFalse();
        assertThat(queries.latestRecon("BANK_RAIL")).isEmpty();
    }

    @Test
    void collectionCaseProjectionKeepsOpenedDetailsAcrossStatusChanges() {
        CollectionCaseProjection projection = new CollectionCaseProjection(cases);
        UUID loanId = UUID.randomUUID();
        projection.onOpened(
                CollectionsCaseOpened.newBuilder()
                        .setMetadata(meta("collections.case-opened", loanId.toString()))
                        .setCaseId("CASE-1")
                        .setLoanId(loanId.toString())
                        .setLoanNumber("LN-1")
                        .setBucket("DPD_1_30")
                        .setDaysPastDue(5)
                        .setPlanVersion(1)
                        .setOpenedAt(Instant.parse("2026-05-01T00:00:00Z"))
                        .build());
        projection.onClosed(
                CollectionsCaseClosed.newBuilder()
                        .setMetadata(meta("collections.case-closed", loanId.toString()))
                        .setCaseId("CASE-1")
                        .setLoanId(loanId.toString())
                        .setLoanNumber("LN-1")
                        .setStatus("RESOLVED")
                        .setReason("paid")
                        .setClosedAt(Instant.parse("2026-05-10T00:00:00Z"))
                        .build());

        assertThat(queries.collectionCases())
                .singleElement()
                .satisfies(
                        c -> {
                            assertThat(c.status()).isEqualTo("CLOSED");
                            assertThat(c.bucket()).isEqualTo("DPD_1_30"); // kept from open
                            assertThat(c.openedAt()).isNotNull();
                        });
    }

    @Test
    void snapshotServiceCapturesTheBusinessDatePortfolio() {
        seedLoan("Active", 0, 30_000_00, 30_000_00, "2026-01");
        SnapshotService service =
                new SnapshotService(
                        loans,
                        snapshots,
                        () -> LocalDate.parse("2026-05-01"),
                        Clock.fixed(Instant.parse("2026-05-01T01:00:00Z"), ZoneOffset.UTC),
                        INR);

        DailySnapshot captured = service.captureDaily();
        assertThat(captured.businessDate()).isEqualTo(LocalDate.parse("2026-05-01"));
        assertThat(captured.totalLoans()).isEqualTo(1);
        assertThat(captured.outstandingPrincipalMinor()).isEqualTo(30_000_00L);
        assertThat(service.recent(30)).hasSize(1);
    }

    private AuditEvent auditEvent(String actor, String action) {
        return AuditEvent.newBuilder()
                .setMetadata(meta("audit.recorded", actor))
                .setActorId(actor)
                .setActorRoles("admin")
                .setAction(action)
                .setTargetType("product")
                .setTargetId("MICRO")
                .setBeforeSummary(null)
                .setAfterSummary("version=2")
                .setOutcome("SUCCESS")
                .setRecordedAt(Instant.parse("2026-05-01T00:00:00Z"))
                .build();
    }

    private LedgerReconCompleted recon(String type, long diff, boolean balanced, Instant at) {
        return LedgerReconCompleted.newBuilder()
                .setMetadata(meta("ledger.recon-completed", UUID.randomUUID().toString()))
                .setReconRunId(UUID.randomUUID().toString())
                .setCurrency("INR")
                .setDiffMinorUnits(diff)
                .setBalanced(balanced)
                .setRunAt(at)
                .setReconType(type)
                .build();
    }
}
