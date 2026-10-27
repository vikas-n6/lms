package com.lms.loanaccount.application;

import com.lms.config.BusinessCalendar;
import com.lms.events.Uuid7;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.application.port.out.WriteOffRequestRepository;
import com.lms.loanaccount.domain.Balances;
import com.lms.loanaccount.domain.Delinquency;
import com.lms.loanaccount.domain.DelinquencySnapshot;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanBalances;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanStateMachine;
import com.lms.loanaccount.domain.LoanTransition.WriteOff;
import com.lms.loanaccount.domain.LoanTransitionResult;
import com.lms.loanaccount.domain.WriteOffRequest;
import com.lms.loanaccount.domain.WriteOffRequestStatus;
import com.lms.schedule.Product;
import com.lms.security.audit.AuditEntry;
import com.lms.security.audit.AuditOutcome;
import com.lms.security.audit.AuditRecorder;
import com.lms.security.audit.AuditSummary;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Two-person write-off (phase prompt task 2, docs/domain-rules.md "Loan state machine"). One admin
 * raises a request with a reason; a <em>different</em> admin approves it. Guards: DPD at least the
 * configured minimum (default 180, versioned policy — never hard-coded in the domain), the same
 * user cannot approve their own request, and the loan must be able to move to {@code WrittenOff}
 * (Delinquent + approval). On approval the outstanding receivables are written off, the loan moves
 * to {@code WrittenOff}, every transition is recorded, and {@code loan.written-off} is published
 * for the ledger to post the bad-debt expense.
 */
public class WriteOffService {

    private final LoanRepository loans;
    private final ScheduleRepository schedules;
    private final WriteOffRequestRepository requests;
    private final StateHistoryRepository history;
    private final com.lms.loanaccount.application.port.out.ProductCatalog products;
    private final LoanEventPublisher publisher;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final AuditRecorder audit;
    private final int minDpd;

    public WriteOffService(
            LoanRepository loans,
            ScheduleRepository schedules,
            WriteOffRequestRepository requests,
            StateHistoryRepository history,
            com.lms.loanaccount.application.port.out.ProductCatalog products,
            LoanEventPublisher publisher,
            BusinessCalendar calendar,
            Clock clock,
            AuditRecorder audit,
            int minDpd) {
        this.loans = Objects.requireNonNull(loans);
        this.schedules = Objects.requireNonNull(schedules);
        this.requests = Objects.requireNonNull(requests);
        this.history = Objects.requireNonNull(history);
        this.products = Objects.requireNonNull(products);
        this.publisher = Objects.requireNonNull(publisher);
        this.calendar = Objects.requireNonNull(calendar);
        this.clock = Objects.requireNonNull(clock);
        this.audit = Objects.requireNonNull(audit);
        if (minDpd < 0) {
            throw new IllegalArgumentException("minDpd must be non-negative");
        }
        this.minDpd = minDpd;
    }

    /** Admin A raises a write-off request. The reason is mandatory ({@link WriteOffRequest}). */
    public WriteOffRequest request(UUID loanId, String reason, String requestedBy) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        DelinquencySnapshot snapshot = assess(loan);
        WriteOffRequest request =
                WriteOffRequest.raise(
                        Uuid7.generate(clock),
                        loanId,
                        loan.loanNumber(),
                        reason,
                        requestedBy,
                        snapshot.daysPastDue(),
                        clock.instant());
        requests.insert(request);
        return request;
    }

    /**
     * Admin B approves the request, writing the loan off (guards enforced here and in the domain).
     */
    public WriteOffRequest approve(UUID requestId, String approver) {
        WriteOffRequest request = requireRequested(requestId);
        Loan loan =
                loans.findById(request.loanId())
                        .orElseThrow(() -> new LoanNotFoundException(request.loanId()));

        DelinquencySnapshot snapshot = assess(loan);
        if (snapshot.daysPastDue() < minDpd) {
            throw new WriteOffNotAllowedException(
                    "DPD " + snapshot.daysPastDue() + " is below the write-off minimum " + minDpd);
        }

        WriteOffRequest approved = request.approve(approver, clock.instant()); // two-person rule

        LoanTransitionResult result =
                LoanStateMachine.transition(loan.state(), new WriteOff(true, true));
        if (!(result instanceof LoanTransitionResult.Moved moved)) {
            String reason = ((LoanTransitionResult.Illegal) result).reason();
            history.append(loan.id(), loan.state(), loan.state(), reason, null, false);
            throw new WriteOffNotAllowedException(
                    "loan " + loan.id() + " cannot be written off: " + reason);
        }

        List<LoanScheduleLine> lines = schedules.findByLoanId(loan.id());
        LoanBalances balances = Balances.of(lines, loan.principal().currency(), calendar.today());

        Loan written = loan.withState(moved.to(), clock.instant());
        loans.update(written);
        history.append(loan.id(), moved.from(), moved.to(), "write-off:" + requestId, null, true);
        schedules.saveAll(lines.stream().map(LoanScheduleLine::writeOff).toList());
        requests.update(approved);
        publisher.writtenOff(
                written,
                requestId.toString(),
                balances.outstandingPrincipal(),
                balances.interestOutstanding(),
                balances.penalOutstanding(),
                request.reason(),
                request.requestedBy(),
                approver,
                null);
        audit.record(
                AuditEntry.of(
                        approver,
                        "",
                        "loan.write-off.approved",
                        "loan",
                        loan.id().toString(),
                        AuditSummary.create()
                                .with("state", moved.from().getClass().getSimpleName())
                                .with("dpd", snapshot.daysPastDue())
                                .render(),
                        AuditSummary.create()
                                .with("state", "WrittenOff")
                                .with("requestId", requestId.toString())
                                .render(),
                        AuditOutcome.SUCCESS));
        return approved;
    }

    /** All write-off requests, newest first, optionally filtered by status (admin queue). */
    public List<WriteOffRequest> listAll(java.util.Optional<String> status) {
        return requests.findAll(status);
    }

    /** Write-off requests for one loan, newest first. */
    public List<WriteOffRequest> listForLoan(UUID loanId) {
        return requests.findByLoanId(loanId);
    }

    /** Reject the request (no state change to the loan). */
    public WriteOffRequest reject(UUID requestId, String decidedBy, String note) {
        WriteOffRequest request = requireRequested(requestId);
        WriteOffRequest rejected = request.reject(decidedBy, note, clock.instant());
        requests.update(rejected);
        // The note is admin free text (possible PII); the audit summary records only the outcome.
        audit.record(
                AuditEntry.of(
                        decidedBy,
                        "",
                        "loan.write-off.rejected",
                        "loan",
                        request.loanId().toString(),
                        null,
                        AuditSummary.create().with("status", "REJECTED").render(),
                        AuditOutcome.SUCCESS));
        return rejected;
    }

    private WriteOffRequest requireRequested(UUID requestId) {
        WriteOffRequest request =
                requests.findById(requestId)
                        .orElseThrow(() -> new WriteOffRequestNotFoundException(requestId));
        if (request.status() != WriteOffRequestStatus.REQUESTED) {
            throw new WriteOffNotAllowedException(
                    "write-off request " + requestId + " is already " + request.status());
        }
        return request;
    }

    private DelinquencySnapshot assess(Loan loan) {
        Product product = products.byVersion(loan.productId(), loan.productVersion());
        return Delinquency.assess(
                schedules.findByLoanId(loan.id()),
                loan.principal().currency(),
                calendar.today(),
                product.graceDays());
    }
}
