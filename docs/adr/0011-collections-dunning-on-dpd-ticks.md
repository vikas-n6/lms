# ADR 0011: Collections dunning advances on DPD ticks, not wall-clock timers

- Status: Accepted
- Date: 2026-10-26

## Context

Phase 4 introduces durable dunning workflows in the `collections` service (CLAUDE.md allows Temporal
"embedded inside Disbursement and Collections only"). Business days are simulated through the shared
`BusinessCalendar` (docs/architecture.md), so a dunning schedule must not be driven by real
wall-clock timers — otherwise a 12-month delinquency scenario could not be exercised in minutes, and
"business day" and "real second" would diverge. The phase prompt is explicit: dunning advances on a
*tick* — each daily `loan.dpd-updated` event is signalled to the workflow, which runs every step
whose DPD threshold has been reached; Temporal durable timers are only for short real-time waits.

## Decision

1. **One workflow per loan, `dun-{loanId}`.** A duplicate start is a no-op
   (`WorkflowExecutionAlreadyStarted` swallowed), so a redelivered open never launches a second
   workflow, and every signal is addressed by the same id.

2. **Ticks drive progression.** The workflow holds the fired-step set and advances only when a
   `DpdTick` (or payment/promise/escalate/write-off) signal arrives. It fires every plan step whose
   `dpdThreshold <= currentDpd` that has not fired. There are **no `Workflow.sleep` calls for dunning
   progression** — the only waiting is `Workflow.await` on signal-set fields. Durable timers remain
   available for action-retry/delivery waits (handled today by Temporal's activity retry, and by the
   `notification` service for channel delivery).

3. **Idempotent actions.** Each action is recorded with a unique `(caseId, stepId)` (DB constraint +
   the in-workflow fired set). A replayed or duplicate `loan.dpd-updated` tick therefore never
   repeats an action — proven in `DunningWorkflowTest` and guarded again in the JDBC adapter.

4. **Signals.** `DpdTick`, `PaymentReceived(overdueCleared)` (cancels remaining steps and resolves),
   `PromiseToPay(date)` (pauses reminders until the business date, resumes automatically when the
   date passes while still overdue), `Escalate`, `AssignAgent`, plus `WrittenOff` (closes the case).
   Dates travel as epoch-days and amounts as minor units so the data converter needs no extra
   modules and the history stays deterministic.

5. **Determinism.** All side effects (persist case/action, publish events) are activities; the
   workflow is pure decision logic. Proven by the in-JVM `TestWorkflowEnvironment` scenario tests and
   the `WorkflowReplayer` determinism test (ADR 0008) — no Docker.

6. **The plan is configuration.** The dunning plan (DPD thresholds → actions, channels, templates)
   is versioned configuration (`lms.dunning.*`), never hard-coded in Java (CLAUDE.md global do-not
   list). A case pins the plan version it was opened under.

## Consequences

- The dunning schedule is reproducible day-by-day against the simulated calendar; the live crash
  test (kill `collections` mid-workflow, restart, continue without repeating actions) is the Docker
  session's to run, but determinism and dedup are already proven in-JVM.
- `collections` never reads loan tables — case state is built purely from the events it consumes
  (ArchUnit keeps the domain Spring/Temporal-free; there is no JDBC path to loan data).
- A second Temporal worker (collections task queue) reuses the ADR 0008 wiring and virtual-thread
  options; no new convergence pins were needed.
