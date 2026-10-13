package com.lms.loanaccount.domain;

import com.lms.loanaccount.domain.LoanState.Active;
import com.lms.loanaccount.domain.LoanState.Cancelled;
import com.lms.loanaccount.domain.LoanState.Closed;
import com.lms.loanaccount.domain.LoanState.Created;
import com.lms.loanaccount.domain.LoanState.Delinquent;
import com.lms.loanaccount.domain.LoanState.Foreclosed;
import com.lms.loanaccount.domain.LoanState.PendingDisbursal;
import com.lms.loanaccount.domain.LoanState.WrittenOff;
import com.lms.loanaccount.domain.LoanTransition.CancelDisbursement;
import com.lms.loanaccount.domain.LoanTransition.ClearOverdue;
import com.lms.loanaccount.domain.LoanTransition.ConfirmDisbursement;
import com.lms.loanaccount.domain.LoanTransition.DraftSchedule;
import com.lms.loanaccount.domain.LoanTransition.Foreclose;
import com.lms.loanaccount.domain.LoanTransition.MarkDelinquent;
import com.lms.loanaccount.domain.LoanTransition.PayInFull;
import com.lms.loanaccount.domain.LoanTransition.WriteOff;
import com.lms.loanaccount.domain.LoanTransitionResult.Illegal;
import com.lms.loanaccount.domain.LoanTransitionResult.Moved;

/**
 * The loan lifecycle as a pure function {@code (state, trigger) -> LoanTransitionResult}, with
 * exhaustive switches and no {@code default} over the sealed {@link LoanState} and {@link
 * LoanTransition} types (docs/domain-rules.md "Loan state machine"). Illegal transitions — a
 * trigger the state does not accept, or one whose guard fails — return {@link Illegal} and leave
 * the state unchanged; the caller audits the rejection in {@code loan_state_history}, never
 * ignoring it.
 *
 * <pre>
 *   Created              --DraftSchedule[provisional exists]--> PendingDisbursal
 *   PendingDisbursal     --ConfirmDisbursement[amount matches]--> Active
 *   PendingDisbursal     --CancelDisbursement[compensated]-----> Cancelled
 *   Active               --MarkDelinquent[>=1 overdue]---------> Delinquent
 *   Delinquent           --ClearOverdue[overdue == 0]----------> Active
 *   Active | Delinquent  --PayInFull[outstanding == 0]---------> Closed
 *   Active | Delinquent  --Foreclose[amount >= quote]----------> Foreclosed
 *   Delinquent           --WriteOff[dpd threshold + approval]--> WrittenOff
 * </pre>
 */
public final class LoanStateMachine {

    private LoanStateMachine() {}

    public static LoanTransitionResult transition(LoanState from, LoanTransition trigger) {
        return switch (from) {
            case Created ignored -> fromCreated(from, trigger);
            case PendingDisbursal ignored -> fromPendingDisbursal(from, trigger);
            case Active ignored -> fromActiveOrDelinquent(from, trigger, false);
            case Delinquent ignored -> fromActiveOrDelinquent(from, trigger, true);
            case Closed ignored -> illegal(from, trigger);
            case Foreclosed ignored -> illegal(from, trigger);
            case WrittenOff ignored -> illegal(from, trigger);
            case Cancelled ignored -> illegal(from, trigger);
        };
    }

    private static LoanTransitionResult fromCreated(LoanState from, LoanTransition trigger) {
        return switch (trigger) {
            case DraftSchedule t ->
                    t.provisionalScheduleExists()
                            ? moved(from, trigger, new PendingDisbursal())
                            : guardFailed(from, trigger, "no provisional schedule drafted");
            case ConfirmDisbursement ignored -> illegal(from, trigger);
            case CancelDisbursement ignored -> illegal(from, trigger);
            case MarkDelinquent ignored -> illegal(from, trigger);
            case ClearOverdue ignored -> illegal(from, trigger);
            case PayInFull ignored -> illegal(from, trigger);
            case Foreclose ignored -> illegal(from, trigger);
            case WriteOff ignored -> illegal(from, trigger);
        };
    }

    private static LoanTransitionResult fromPendingDisbursal(
            LoanState from, LoanTransition trigger) {
        return switch (trigger) {
            case ConfirmDisbursement t ->
                    t.amountMatches()
                            ? moved(from, trigger, new Active())
                            : guardFailed(
                                    from,
                                    trigger,
                                    "disbursed net does not match principal-fee-gst");
            case CancelDisbursement t ->
                    t.compensationCompleted()
                            ? moved(from, trigger, new Cancelled())
                            : guardFailed(from, trigger, "compensation not completed");
            case DraftSchedule ignored -> illegal(from, trigger);
            case MarkDelinquent ignored -> illegal(from, trigger);
            case ClearOverdue ignored -> illegal(from, trigger);
            case PayInFull ignored -> illegal(from, trigger);
            case Foreclose ignored -> illegal(from, trigger);
            case WriteOff ignored -> illegal(from, trigger);
        };
    }

    private static LoanTransitionResult fromActiveOrDelinquent(
            LoanState from, LoanTransition trigger, boolean delinquent) {
        return switch (trigger) {
            case MarkDelinquent t ->
                    delinquent
                            ? illegal(from, trigger)
                            : (t.overdueInstalmentCount() >= 1
                                    ? moved(from, trigger, new Delinquent())
                                    : guardFailed(from, trigger, "no overdue instalment"));
            case ClearOverdue t ->
                    !delinquent
                            ? illegal(from, trigger)
                            : (t.overdueTotalZero()
                                    ? moved(from, trigger, new Active())
                                    : guardFailed(from, trigger, "overdue total is not zero"));
            case PayInFull t ->
                    t.outstandingZero()
                            ? moved(from, trigger, new Closed())
                            : guardFailed(from, trigger, "outstanding total is not zero");
            case Foreclose t ->
                    t.amountCoversQuote()
                            ? moved(from, trigger, new Foreclosed())
                            : guardFailed(
                                    from, trigger, "payment does not cover the foreclosure quote");
            case WriteOff t ->
                    !delinquent
                            ? illegal(from, trigger)
                            : (t.dpdThresholdMet() && t.approvalRecordPresent()
                                    ? moved(from, trigger, new WrittenOff())
                                    : guardFailed(
                                            from,
                                            trigger,
                                            "DPD threshold not met or approval record missing"));
            case DraftSchedule ignored -> illegal(from, trigger);
            case ConfirmDisbursement ignored -> illegal(from, trigger);
            case CancelDisbursement ignored -> illegal(from, trigger);
        };
    }

    private static LoanTransitionResult moved(
            LoanState from, LoanTransition trigger, LoanState to) {
        return new Moved(from, trigger, to);
    }

    private static LoanTransitionResult illegal(LoanState from, LoanTransition trigger) {
        return new Illegal(from, trigger, "transition not legal from " + from.code());
    }

    private static LoanTransitionResult guardFailed(
            LoanState from, LoanTransition trigger, String why) {
        return new Illegal(from, trigger, "guard failed: " + why);
    }
}
