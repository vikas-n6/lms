package com.lms.collections.application;

import com.lms.collections.application.port.out.CollectionCaseRepository;
import com.lms.collections.application.port.out.CollectionsEventPublisher;
import com.lms.collections.application.port.out.DunningPlanProvider;
import com.lms.collections.application.port.out.DunningWorkflowClient;
import com.lms.collections.domain.CollectionCase;
import com.lms.events.Uuid7;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reacts to the loan/repayment events collections consumes (phase prompt task 4). It never reads
 * loan tables — it opens and advances cases purely from events (docs/architecture.md). A case opens
 * at DPD 1 or more and starts {@code dun-{loanId}}; every subsequent DPD tick is signalled to the
 * workflow, which decides which steps fire. Admin actions (assign, promise, escalate, resolve) also
 * route here.
 */
public class CollectionsService {

    private static final Logger log = LoggerFactory.getLogger(CollectionsService.class);

    private final CollectionCaseRepository cases;
    private final DunningWorkflowClient workflow;
    private final CollectionsEventPublisher publisher;
    private final DunningPlanProvider plans;
    private final Clock clock;

    public CollectionsService(
            CollectionCaseRepository cases,
            DunningWorkflowClient workflow,
            CollectionsEventPublisher publisher,
            DunningPlanProvider plans,
            Clock clock) {
        this.cases = Objects.requireNonNull(cases);
        this.workflow = Objects.requireNonNull(workflow);
        this.publisher = Objects.requireNonNull(publisher);
        this.plans = Objects.requireNonNull(plans);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Handle {@code loan.dpd-updated}: open a case at DPD&ge;1 and signal the tick, or clear at 0.
     */
    public void onDpdUpdated(
            String loanId,
            String loanNumber,
            int daysPastDue,
            String bucket,
            LocalDate businessDate) {
        Optional<CollectionCase> existing = cases.findByLoanId(loanId);
        if (daysPastDue <= 0) {
            existing.ifPresent(c -> workflow.signalPaymentReceived(loanId, true));
            return;
        }
        CollectionCase theCase =
                existing.orElseGet(() -> open(loanId, loanNumber, bucket, daysPastDue));
        if (existing.isPresent()) {
            cases.update(existing.get().withDpd(bucket, daysPastDue, clock.instant()));
        }
        workflow.signalDpdTick(loanId, daysPastDue, bucket, businessDate);
        log.debug("signalled DPD tick {} for case {}", daysPastDue, theCase.id());
    }

    private CollectionCase open(String loanId, String loanNumber, String bucket, int dpd) {
        var plan = plans.current();
        CollectionCase theCase =
                CollectionCase.open(
                        Uuid7.generate(clock),
                        loanId,
                        loanNumber,
                        bucket,
                        dpd,
                        plan.version(),
                        clock.instant());
        cases.insert(theCase);
        publisher.caseOpened(theCase);
        workflow.start(theCase, plan);
        return theCase;
    }

    /** A repayment was allocated; a cleared overdue is confirmed by the DPD-0 tick. */
    public void onRepaymentAllocated(String loanId) {
        cases.findByLoanId(loanId).ifPresent(c -> workflow.signalPaymentReceived(loanId, false));
    }

    /** A mandate auto-debit failed; the DPD-7 re-presentation step covers the retry. */
    public void onMandateDebitFailed(String loanId, String failureCode) {
        log.info(
                "mandate debit failed for loan {} ({}); dunning continues on ticks",
                loanId,
                failureCode);
    }

    /** The loan was fully paid and closed: resolve the case. */
    public void onLoanClosed(String loanId) {
        cases.findByLoanId(loanId)
                .filter(c -> !c.status().isTerminal())
                .ifPresent(c -> workflow.signalPaymentReceived(loanId, true));
    }

    /** The loan was written off: the workflow closes the case as WRITTEN_OFF. */
    public void onLoanWrittenOff(String loanId) {
        cases.findByLoanId(loanId)
                .filter(c -> !c.status().isTerminal())
                .ifPresent(c -> workflow.signalWrittenOff(loanId));
    }

    // --- Admin actions -------------------------------------------------------------------------

    public void assignAgent(UUID caseId, String agent) {
        CollectionCase c = require(caseId);
        cases.update(c.withAgent(agent, clock.instant()));
        workflow.signalAssignAgent(c.loanId(), agent);
    }

    public void recordPromise(UUID caseId, LocalDate date, long amountMinor, String currency) {
        CollectionCase c = require(caseId);
        cases.update(c.withPromise(date, amountMinor, currency, clock.instant()));
        workflow.signalPromiseToPay(c.loanId(), date, amountMinor, currency);
    }

    public void escalate(UUID caseId, String reason) {
        CollectionCase c = require(caseId);
        workflow.signalEscalate(c.loanId(), reason);
    }

    /** Manually resolve a case (admin), ending the dunning workflow. */
    public void resolve(UUID caseId, String reason) {
        CollectionCase c = require(caseId);
        workflow.signalPaymentReceived(c.loanId(), true);
        log.info("manual resolve requested for case {}: {}", caseId, reason);
    }

    private CollectionCase require(UUID caseId) {
        return cases.findById(caseId)
                .orElseThrow(() -> new CollectionCaseNotFoundException(caseId));
    }
}
