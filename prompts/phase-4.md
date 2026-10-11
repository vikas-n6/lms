# Phase 4 prompt (paste this whole file into Claude Code)

You are working in the `lms` repository on Phase 4.

1. Read CLAUDE.md, docs/architecture.md, docs/domain-rules.md and every ADR in docs/adr/.
2. Confirm the git tag phase-3-done exists and that `./mvnw clean verify` passes.
   If either fails, stop and tell me instead of continuing.
3. Write your plan to docs/plans/phase-4.md (tasks in order, risks, open questions),
   then start work. Ask me only if something changes money handling or conflicts with
   a locked decision.
4. Work in small vertical slices. After each slice run the fast loop for the touched
   modules, fix failures, and commit with a conventional commit message.
5. Write tests first for money, schedules, ledger postings and state transitions.
6. Record non-trivial decisions as ADRs. Never weaken a test or rule to get green.
7. At the end run `./mvnw clean verify` and the phase e2e script, then print the exit
   checklist with pass or fail for each item.

The phase specification follows.

---

## Phase 4: Collections and operations

**Goal:** delinquent loans move through DPD buckets, penal charges accrue, dunning runs as a durable workflow, notifications go out reliably, and a daily reconciliation compares the ledger with the simulated bank and rail reports.

**Read first:** `CLAUDE.md`, `docs/domain-rules.md` (accrual, state machine, DPD buckets, ledger templates), Phase 3 ADRs. Write the plan to `docs/plans/phase-4.md`, then proceed.

**Time design decision (record as an ADR):** business days are simulated through the business calendar, so wall-clock timers cannot drive dunning. The dunning workflow advances on a *tick*: each daily `loan.dpd-updated` event is signalled to the workflow, which runs every step whose DPD threshold has been reached. Temporal durable timers are used only for short real-time waits such as action retries and delivery timeouts.

### Tasks

1. **`loan-account` daily jobs.** Run in this order for each business date, each idempotent per `(loanId, businessDate)`: mark instalments `DUE` and `OVERDUE`, interest accrual (Phase 2), penal accrual (36% APR simple on overdue principal plus interest after 3 grace days), DPD computation. Publish `loan.penal-accrued`, and `loan.dpd-updated` (bucket, DPD, overdue amounts) for every loan with DPD above zero each day, plus once when it returns to zero. Move loans between `Active` and `Delinquent` as in `docs/domain-rules.md`, and flag `NPA` at DPD 91 or more. Add a pre-due job publishing `loan.installment-due-soon` three days before each due date.
2. **Write-off with two-person approval.** `POST /loans/{id}/write-off-requests` (admin A) then `POST /write-off-requests/{id}/approve` (a different admin B). Guards: DPD at least 180, same user cannot approve their own request, reason required. On approval, publish `loan.written-off`.
3. **`ledger` templates.** Add `loan.penal-accrued` and `loan.written-off` (debit bad debt expense and reverse accrued interest and penal income, credit the receivables).
4. **`collections` service with Temporal.**
    - Consume `loan.dpd-updated`, `loan.repayment-allocated`, `mandate.debit-failed`, `loan.closed`, `loan.written-off`.
    - Open a `collection_case` at DPD 1 or more and start `DunningWorkflow` with id `dun-{loanId}`. Statuses: `OPEN`, `PROMISE_TO_PAY`, `ESCALATED`, `RESOLVED`, `WRITE_OFF_RECOMMENDED`, `WRITTEN_OFF`.
    - Default dunning plan (a versioned config, never hard-coded): DPD 1 SMS reminder. DPD 3 SMS and email. DPD 7 SMS plus a mandate re-presentation request. DPD 15 escalate to the agent queue. DPD 30 formal notice. DPD 60 final notice. DPD 91 NPA review. DPD 180 recommend write-off.
    - Signals: `DpdTick`, `PaymentReceived` (cancels remaining steps when overdue is cleared), `PromiseToPay(date, amount)` (pauses reminders until that business date, resumes automatically if broken), `Escalate`, `AssignAgent`.
    - Every action is recorded in `dunning_actions` with a unique `(caseId, stepId)` so replays and duplicate ticks never repeat an action. Publish `collections.case-opened`, `collections.reminder-due`, `collections.escalated`, `collections.case-closed`.
    - APIs: case list (filters: bucket, status, agent), case detail with the action timeline, add note, assign, record a promise to pay, escalate, manually resolve.
5. **`notification` service (full).**
    - Versioned templates with placeholders. Channels: SMS, email, push, WhatsApp, all through simulators with channel-specific failure settings.
    - Subscribe to: `application.offered`, `disbursement.failed`, `loan.activated`, `loan.installment-due-soon`, `repayment.received`, `repayment.reversed`, `mandate.debit-failed`, `collections.reminder-due`, `loan.closed`.
    - Deduplicate on `(eventId, templateId, channel)`. Retry with backoff. Statuses `QUEUED`, `SENT`, `FAILED`, `SUPPRESSED`.
    - Respect consent and a quiet-hours window (default 21:00 to 08:00 business-calendar time): held messages are released in the morning.
6. **Reconciliation (in `ledger`).**
    - Simulators expose `GET /sim/bank/statement?date=` and `GET /sim/rail/settlement-report?date=`, with a `reconDriftRate` knob that injects discrepancies.
    - A daily job matches statement and report lines to postings on account 1001 by reference (UTR or provider reference) and amount. Classify each line as `MATCHED`, `MISSING_IN_LEDGER`, `MISSING_IN_BANK`, `AMOUNT_MISMATCH` or `DUPLICATE`. Store `recon_runs` and `recon_items`. Publish `ledger.recon-completed`.
    - Admin can resolve an item with a note, or raise an adjusting entry through the ledger's reversal mechanism.
7. **Gateway routes** for collections, write-off requests, notifications and reconciliation.
8. **Admin UI pages.** **Collections queue** (DPD bucket summary cards, case table with filters and agent assignment). Case detail with the dunning timeline and a promise-to-pay form. Write-off requests (approve and reject with the two-person rule visible). Notification log with status filters. **Reconciliation results** (runs, items, resolution).
9. **E2E script** `scripts/e2e/phase4.sh`: create three loans and advance the business date day by day, running the jobs in order each day. Loan A pays on time throughout. Loan B misses two instalments, then pays and returns to `Active`. Loan C stops paying, reaches NPA, is recommended for write-off, and is written off by two different admins.

### Acceptance tests

- Loan A never opens a case. Loan B opens a case at DPD 1, runs the DPD 1, 3 and 7 steps, and the case resolves with no further steps once payment clears the overdue amount. Loan C progresses through every step to write-off.
- Duplicate or replayed `loan.dpd-updated` ticks never repeat a dunning action.
- A promise to pay pauses reminders. A broken promise resumes them on the next tick.
- A payment during the workflow cancels the remaining steps.
- Penal accrual: amounts match the formula for a hand-computed example, accrue only after the grace days, and are idempotent per date.
- Write-off: a request by admin A approved by admin A is rejected. Approval by admin B succeeds only at DPD of 180 or more.
- After write-off, the ledger entry balances, the loan's receivables are zero, and bad debt expense equals the written-off total.
- Notifications: replaying an event sends no second message. A message created at 22:00 is released the next morning. A failing channel retries and ends as `FAILED` after the configured attempts.
- Reconciliation: a clean day gives all `MATCHED`. With drift injected, each of the four discrepancy types is detected exactly.
- Crash test: kill `collections` mid-workflow, restart, and see the workflow continue without repeating actions.
- The trial balance is zero at the end of the 12-month scenario.

### Do not

- Do not drive business-day logic with wall-clock timers.
- Do not hard-code the dunning plan or notification texts in Java.
- Do not let `collections` read loan tables. It reacts to events.
- Do not allow a single user to both request and approve a write-off.

### Exit checklist

- [ ] `./mvnw clean verify` green
- [ ] `scripts/e2e/phase4.sh` passes for all three loans
- [ ] Reconciliation detects all injected discrepancy types
- [ ] Collections queue, write-off and reconciliation pages work
- [ ] Tag `phase-4-done`
