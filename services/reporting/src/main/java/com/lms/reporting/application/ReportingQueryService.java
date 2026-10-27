package com.lms.reporting.application;

import com.lms.reporting.application.port.out.ApplicationReadRepository;
import com.lms.reporting.application.port.out.AuditLogRepository;
import com.lms.reporting.application.port.out.CollectionCaseRepository;
import com.lms.reporting.application.port.out.CollectionCounterRepository;
import com.lms.reporting.application.port.out.LoanReadRepository;
import com.lms.reporting.application.port.out.ReconStatusRepository;
import com.lms.reporting.domain.ApplicationFunnel;
import com.lms.reporting.domain.AuditLogRecord;
import com.lms.reporting.domain.AuditQuery;
import com.lms.reporting.domain.CollectionCaseInfo;
import com.lms.reporting.domain.CollectionEfficiency;
import com.lms.reporting.domain.PortfolioMetrics;
import com.lms.reporting.domain.ReconStatus;
import com.lms.reporting.domain.VintageCurve;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-side queries that compute the dashboard metrics from the denormalised read models. All
 * aggregation is delegated to the pure domain functions (ADR 0013), so the figures are a
 * deterministic function of the read rows and a rebuild from offset 0 yields identical numbers.
 */
public class ReportingQueryService {

    private static final int MAX_AUDIT_LIMIT = 500;

    private final LoanReadRepository loans;
    private final ApplicationReadRepository applications;
    private final CollectionCounterRepository counters;
    private final AuditLogRepository auditLog;
    private final ReconStatusRepository reconStatus;
    private final CollectionCaseRepository cases;
    private final Currency currency;

    public ReportingQueryService(
            LoanReadRepository loans,
            ApplicationReadRepository applications,
            CollectionCounterRepository counters,
            AuditLogRepository auditLog,
            ReconStatusRepository reconStatus,
            CollectionCaseRepository cases,
            Currency currency) {
        this.loans = Objects.requireNonNull(loans);
        this.applications = Objects.requireNonNull(applications);
        this.counters = Objects.requireNonNull(counters);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.reconStatus = Objects.requireNonNull(reconStatus);
        this.cases = Objects.requireNonNull(cases);
        this.currency = Objects.requireNonNull(currency);
    }

    public PortfolioMetrics portfolio() {
        return PortfolioMetrics.from(loans.findAll(), currency);
    }

    public ApplicationFunnel funnel() {
        return ApplicationFunnel.from(applications.findAll());
    }

    public CollectionEfficiency collectionEfficiency() {
        return CollectionEfficiency.of(counters.billed(currency), counters.collected(currency));
    }

    public VintageCurve vintage() {
        return VintageCurve.from(loans.findAll());
    }

    public List<AuditLogRecord> audit(AuditQuery query) {
        int limit = query.limit() <= 0 ? MAX_AUDIT_LIMIT : Math.min(query.limit(), MAX_AUDIT_LIMIT);
        return auditLog.query(
                new AuditQuery(
                        query.actorId(),
                        query.action(),
                        query.targetType(),
                        query.targetId(),
                        query.from(),
                        query.to(),
                        limit));
    }

    public Optional<ReconStatus> latestRecon(String reconType) {
        return reconStatus.latest(reconType);
    }

    public List<CollectionCaseInfo> collectionCases() {
        return cases.findAll();
    }
}
