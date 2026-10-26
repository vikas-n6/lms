package com.lms.collections.adapters.out.temporal;

import com.lms.collections.adapters.out.temporal.DunningCommands.DpdTick;
import com.lms.collections.adapters.out.temporal.DunningCommands.PromiseToPay;
import com.lms.collections.adapters.out.temporal.DunningCommands.StartDunningCommand;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * The durable dunning workflow (phase prompt task 4), one per loan, id {@code dun-{loanId}} so a
 * duplicate start is a no-op. It advances on DPD <em>ticks</em> (business-calendar driven), not
 * wall-clock timers; it fires every plan step whose DPD threshold has been reached, pausing while a
 * promise-to-pay is open and resuming when it is broken, and cancels the remaining steps once a
 * payment clears the overdue amount (ADR 0011).
 */
@WorkflowInterface
public interface DunningWorkflow {

    @WorkflowMethod
    void run(StartDunningCommand command);

    /** A daily DPD tick ({@code loan.dpd-updated} with DPD &gt; 0). */
    @SignalMethod
    void onDpdTick(DpdTick tick);

    /** A payment was applied; {@code overdueCleared} true cancels the remaining steps (resolve). */
    @SignalMethod
    void onPaymentReceived(boolean overdueCleared);

    /** A promise to pay: reminders pause until the promised business date. */
    @SignalMethod
    void onPromiseToPay(PromiseToPay promise);

    /** Manually escalate the case to the agent queue. */
    @SignalMethod
    void onEscalate(String reason);

    /** Assign an agent to the case. */
    @SignalMethod
    void onAssignAgent(String agent);

    /** The loan was written off; the case closes as WRITTEN_OFF. */
    @SignalMethod
    void onWrittenOff();

    /** Current case status for queries / the admin UI. */
    @QueryMethod
    String currentStatus();
}
