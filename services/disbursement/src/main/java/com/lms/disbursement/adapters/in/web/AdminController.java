package com.lms.disbursement.adapters.in.web;

import com.lms.disbursement.application.DisbursementQueryService;
import com.lms.disbursement.application.port.out.WorkflowStarter;
import com.lms.disbursement.domain.Disbursement;
import com.lms.disbursement.domain.PayoutAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin surface for the disbursement monitor (phase prompt S7): list, detail with attempts and a
 * Temporal-UI link, and retry/cancel of a disbursement parked in MANUAL_REVIEW (delivered to the
 * workflow as an admin-decision signal).
 */
@RestController
@RequestMapping("/disbursements")
public class AdminController {

    private final DisbursementQueryService query;
    private final WorkflowStarter workflows;
    private final String temporalUiBaseUrl;

    public AdminController(
            DisbursementQueryService query,
            WorkflowStarter workflows,
            @Value("${lms.temporal.ui-base-url:http://localhost:8233}") String temporalUiBaseUrl) {
        this.query = query;
        this.workflows = workflows;
        this.temporalUiBaseUrl = temporalUiBaseUrl;
    }

    @GetMapping
    public List<DisbursementSummary> list() {
        return query.list().stream().map(AdminController::summary).toList();
    }

    @GetMapping("/{id}")
    public DisbursementDetail get(@PathVariable UUID id) {
        Disbursement d = query.get(id);
        List<PayoutAttempt> attempts = query.attempts(id);
        return new DisbursementDetail(summary(d), attempts, temporalLink(d.loanId()));
    }

    @PostMapping("/{id}/retry")
    public void retry(@PathVariable UUID id) {
        workflows.signalAdminDecision(query.get(id).loanId(), true);
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id) {
        workflows.signalAdminDecision(query.get(id).loanId(), false);
    }

    private String temporalLink(UUID loanId) {
        return temporalUiBaseUrl + "/namespaces/default/workflows/disb-" + loanId;
    }

    private static DisbursementSummary summary(Disbursement d) {
        return new DisbursementSummary(
                d.id(),
                d.loanId(),
                d.loanNumber(),
                d.status().name(),
                d.net().minorUnits(),
                d.net().currency().getCurrencyCode(),
                d.railRef(),
                d.utr(),
                d.attempts(),
                d.failureReason());
    }

    public record DisbursementSummary(
            UUID id,
            UUID loanId,
            String loanNumber,
            String status,
            long netMinorUnits,
            String currency,
            String railRef,
            String utr,
            int attempts,
            String failureReason) {}

    public record DisbursementDetail(
            DisbursementSummary disbursement,
            List<PayoutAttempt> attempts,
            String temporalUiLink) {}
}
