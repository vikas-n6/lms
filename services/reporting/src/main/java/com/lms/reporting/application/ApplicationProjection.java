package com.lms.reporting.application;

import com.lms.contracts.application.ApplicationAccepted;
import com.lms.contracts.application.ApplicationExpired;
import com.lms.contracts.application.ApplicationOffered;
import com.lms.contracts.application.ApplicationRejected;
import com.lms.contracts.application.ApplicationSubmitted;
import com.lms.contracts.decision.DecisionCompleted;
import com.lms.reporting.application.port.out.ApplicationReadRepository;
import com.lms.reporting.domain.ApplicationFunnelRow;
import java.util.Objects;

/**
 * Projects application.events and decision.events onto the funnel read model (ADR 0013). Each
 * handler merges one stage flag (OR semantics), so the projection is order-independent and a
 * replayed or duplicate event never undoes a reached stage. The disbursed stage is set by {@link
 * LoanProjection} when the loan activates.
 */
public class ApplicationProjection {

    private final ApplicationReadRepository applications;

    public ApplicationProjection(ApplicationReadRepository applications) {
        this.applications = Objects.requireNonNull(applications);
    }

    public void onSubmitted(ApplicationSubmitted e) {
        merge(
                e.getApplicationId(),
                e.getCustomerId(),
                flags(true, false, false, false, false, false));
    }

    public void onDecisionCompleted(DecisionCompleted e) {
        String outcome = e.getOutcome();
        boolean approved = "APPROVE".equals(outcome);
        boolean rejected = "REJECT".equals(outcome);
        // REFER leaves the application pending; neither flag is set.
        merge(
                e.getApplicationId(),
                e.getCustomerId(),
                flags(false, approved, false, false, false, rejected));
    }

    public void onOffered(ApplicationOffered e) {
        // An offer implies the application was approved.
        merge(
                e.getApplicationId(),
                e.getCustomerId(),
                flags(false, true, true, false, false, false));
    }

    public void onAccepted(ApplicationAccepted e) {
        merge(
                e.getApplicationId(),
                e.getCustomerId(),
                flags(false, true, true, true, false, false));
    }

    public void onRejected(ApplicationRejected e) {
        merge(
                e.getApplicationId(),
                e.getCustomerId(),
                flags(false, false, false, false, false, true));
    }

    public void onExpired(ApplicationExpired e) {
        ApplicationFunnelRow expired =
                new ApplicationFunnelRow(null, false, false, false, false, false, false, true);
        merge(e.getApplicationId(), e.getCustomerId(), expired);
    }

    private void merge(String applicationId, String customerId, ApplicationFunnelRow flags) {
        applications.merge(applicationId, customerId, flags);
    }

    private static ApplicationFunnelRow flags(
            boolean submitted,
            boolean approved,
            boolean offered,
            boolean accepted,
            boolean disbursed,
            boolean rejected) {
        return new ApplicationFunnelRow(
                null, submitted, approved, offered, accepted, disbursed, rejected, false);
    }
}
