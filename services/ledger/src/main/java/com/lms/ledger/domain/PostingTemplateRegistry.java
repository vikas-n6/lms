package com.lms.ledger.domain;

import com.lms.ledger.domain.templates.DisbursementTemplates;
import com.lms.ledger.domain.templates.InterestAccruedTemplate;
import com.lms.ledger.domain.templates.RepaymentTemplates;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Selects the {@link PostingTemplate} for a source event type (docs/domain-rules.md "Posting
 * templates"). Phase 2 registers disbursement (initiated/succeeded/failed) and interest accrual;
 * Phase 3 adds repayment (received/allocated/reversed/allocation-reversed) and the overpayment
 * refund; penal and write-off templates register here in Phase 4 without touching callers.
 */
public final class PostingTemplateRegistry {

    private final Map<String, PostingTemplate> byEventType = new LinkedHashMap<>();

    public PostingTemplateRegistry(List<PostingTemplate> templates) {
        for (PostingTemplate template : templates) {
            byEventType.put(template.eventType(), template);
        }
    }

    /** The default Phase 2 registry. */
    public static PostingTemplateRegistry defaults() {
        return new PostingTemplateRegistry(
                List.of(
                        new DisbursementTemplates.Initiated(),
                        new DisbursementTemplates.Succeeded(),
                        new DisbursementTemplates.Failed(),
                        new InterestAccruedTemplate(),
                        new RepaymentTemplates.Received(),
                        new RepaymentTemplates.Allocated(),
                        new RepaymentTemplates.Reversed(),
                        new RepaymentTemplates.AllocationReversed(),
                        new RepaymentTemplates.OverpaymentRefund()));
    }

    public Optional<PostingTemplate> find(String eventType) {
        return Optional.ofNullable(byEventType.get(eventType));
    }

    public boolean handles(String eventType) {
        return byEventType.containsKey(eventType);
    }
}
