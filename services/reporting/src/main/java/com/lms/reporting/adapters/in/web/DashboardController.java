package com.lms.reporting.adapters.in.web;

import com.lms.reporting.application.ReportingQueryService;
import com.lms.reporting.application.SnapshotService;
import com.lms.reporting.domain.ApplicationFunnel;
import com.lms.reporting.domain.CollectionCaseInfo;
import com.lms.reporting.domain.CollectionEfficiency;
import com.lms.reporting.domain.DailySnapshot;
import com.lms.reporting.domain.DpdBucketCategory;
import com.lms.reporting.domain.PortfolioMetrics;
import com.lms.reporting.domain.ReconStatus;
import com.lms.reporting.domain.VintageCurve;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only dashboard queries from the reporting read models (ADR 0013). Money is returned as
 * {@code {minorUnits, currency}} and ratios as integer basis points; the Admin UI formats both from
 * the currency code (docs/domain-rules.md). Routed behind the gateway at {@code /api/reporting/**}.
 */
@RestController
@RequestMapping("/reporting")
public class DashboardController {

    private final ReportingQueryService queries;
    private final SnapshotService snapshots;

    public DashboardController(ReportingQueryService queries, SnapshotService snapshots) {
        this.queries = queries;
        this.snapshots = snapshots;
    }

    @GetMapping("/portfolio")
    public PortfolioMetrics portfolio() {
        return queries.portfolio();
    }

    @GetMapping("/funnel")
    public ApplicationFunnel funnel() {
        return queries.funnel();
    }

    @GetMapping("/collection-efficiency")
    public CollectionEfficiency collectionEfficiency() {
        return queries.collectionEfficiency();
    }

    @GetMapping("/vintage")
    public VintageCurve vintage() {
        return queries.vintage();
    }

    @GetMapping("/dpd-distribution")
    public Map<DpdBucketCategory, Integer> dpdDistribution() {
        return queries.portfolio().dpdDistribution();
    }

    @GetMapping("/collection-cases")
    public List<CollectionCaseInfo> collectionCases() {
        return queries.collectionCases();
    }

    @GetMapping("/recon/{type}")
    public ResponseEntity<ReconStatus> recon(@PathVariable String type) {
        return queries.latestRecon(type)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/snapshots")
    public List<DailySnapshot> snapshots(@RequestParam(defaultValue = "30") int limit) {
        return snapshots.recent(limit);
    }

    /**
     * Capture the current business date's snapshot (invoked by the daily job or by the e2e run).
     */
    @PostMapping("/snapshots/capture")
    public DailySnapshot capture() {
        return snapshots.captureDaily();
    }
}
