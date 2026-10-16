package com.lms.disbursement.application;

import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.WorkflowStarter;
import com.lms.disbursement.domain.Disbursement;
import com.lms.events.Uuid7;
import com.lms.money.Money;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@code loan.created}: persists a disbursement and starts its payout workflow. Idempotent
 * on the loan id (unique in the DB and the workflow id {@code disb-{loanId}}), so a redelivered
 * {@code loan.created} is a no-op — a duplicate start never issues a second payout.
 */
public class RegisterDisbursementService {

    private final DisbursementRepository disbursements;
    private final WorkflowStarter workflows;
    private final Clock clock;

    public RegisterDisbursementService(
            DisbursementRepository disbursements, WorkflowStarter workflows, Clock clock) {
        this.disbursements = Objects.requireNonNull(disbursements);
        this.workflows = Objects.requireNonNull(workflows);
        this.clock = Objects.requireNonNull(clock);
    }

    public void register(NewDisbursement command) {
        if (disbursements.findByLoanId(command.loanId()).isPresent()) {
            return; // duplicate start: no-op
        }
        Disbursement disbursement =
                Disbursement.initiate(
                        Uuid7.generate(clock),
                        command.loanId(),
                        command.loanNumber(),
                        command.principal(),
                        command.fee(),
                        command.gst(),
                        command.net(),
                        clock.instant());
        disbursements.save(disbursement);
        workflows.startDisbursement(
                disbursement.id(), disbursement.loanId(), disbursement.loanNumber());
    }

    /** Minimal data lifted from {@code loan.created} to start a payout. */
    public record NewDisbursement(
            UUID loanId, String loanNumber, Money principal, Money fee, Money gst, Money net) {}
}
