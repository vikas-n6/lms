package com.lms.collections.adapters.out.temporal;

import java.util.List;

/**
 * Serializable value types exchanged with the {@link DunningWorkflow} (phase prompt task 4). Dates
 * travel as epoch-days and amounts as minor units so the Temporal data converter needs no extra
 * modules, and the workflow stays deterministic.
 */
public final class DunningCommands {

    private DunningCommands() {}

    /** The start command: the case identity and the plan (steps) it runs under. */
    public record StartDunningCommand(
            String caseId,
            String loanId,
            String loanNumber,
            int planVersion,
            List<StepDef> steps) {}

    /** A plan step, flattened for serialization (actionType as its enum name). */
    public record StepDef(
            String stepId,
            int dpdThreshold,
            String actionType,
            String channel,
            String templateId) {}

    /** A daily DPD tick signalled from {@code loan.dpd-updated}. */
    public record DpdTick(int daysPastDue, String bucket, long businessEpochDay) {}

    /** A promise to pay by a business date (amount is for display only). */
    public record PromiseToPay(long promiseEpochDay, long amountMinor, String currency) {}

    /** The step the workflow asks the activity to fire. */
    public record FiredStep(
            String stepId,
            int dpdThreshold,
            String actionType,
            String channel,
            String templateId) {}
}
