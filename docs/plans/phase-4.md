# Phase 4 plan — Collections and operations

Goal (phase prompt): delinquent loans move through DPD buckets, penal charges accrue, dunning runs
as a durable Temporal workflow, notifications go out reliably, and a daily reconciliation compares
the ledger with the simulated bank and rail reports.

Pre-flight (step 2): `phase-3-done` tag is **withheld by design** (no Docker locally; README records
it). `./mvnw clean verify` is **green**. User authorised proceeding with the non-Docker work and
withholding `phase-4-done` for the same batched Docker session (mirrors Phase 3).

## Scope boundary (Docker-deferred vs done-now)

Same discipline as Phases 1–3. **Done now** (must be `./mvnw clean verify` green): all domain money
and state logic test-first; Temporal dunning proven in-JVM via `TestWorkflowEnvironment` +
`WorkflowReplayer`; notification dedup/retry/quiet-hours/consent logic; reconciliation matcher;
Avro serde; ArchUnit; ADRs. **Docker session**: `scripts/e2e/phase4.sh` against live broker +
Temporal dev server + simulators + Postgres; the crash/restart test; compose health; OIDC UI; k6.
Tag `phase-4-done` only after those pass.

## Locked-decision check

Nothing changes money handling or a locked decision. Temporal is now embedded in **collections**
too (CLAUDE.md already allows "Disbursement and Collections only"). No new product, no new topic or
event type outside the architecture catalogue (collections.*, notification.*, ledger.recon-completed
are all catalogued). New Avro events are additive/backward-compatible. No ask required.

## Tasks, in order (small vertical slices, each build-green + conventional commit)

1. **Contracts — Avro events (additive, BACKWARD-safe).** New: `LoanPenalAccrued`, `LoanDpdUpdated`,
   `LoanInstallmentDueSoon`, `LoanWrittenOff` (loan.events); `CollectionsCaseOpened`,
   `CollectionsReminderDue`, `CollectionsEscalated`, `CollectionsCaseClosed` (collections.events);
   `NotificationSent`, `NotificationFailed` (notification.events). Extend `LedgerReconCompleted` with
   optional `reconType` (default `TRIAL_BALANCE`) + classification counts (defaults 0) so the
   existing trial-balance publish stays valid and the bank/rail run reuses the catalogued event
   (ADR). Add `WriteOffRequested`/`WriteOffApproved`? No — write-off is internal to loan-account
   until approval; only `loan.written-off` is published.

2. **loan-account daily jobs (test-first).** Order per business date, each idempotent per
   `(loanId, businessDate)`: (a) mark DUE/OVERDUE (derived, already in `ScheduleItemState`), (b)
   interest accrual (exists), (c) penal accrual (`PenalAccrual.forDay`, exists; add service +
   `accruals` kind=PENAL + bump `schedule_items.penal_accrued`), (d) DPD compute + bucket + NPA flag.
   Publish `loan.penal-accrued`, `loan.dpd-updated` (every loan with DPD>0 each day, plus once on
   return to 0), and a pre-due job `loan.installment-due-soon` three days before each due date. Move
   `Active`↔`Delinquent` via the existing state machine + `loan_state_history`. Extend
   `LoanEventPublisher`, `AccrualRepository`, `AdminController` job names, add `PenalAccrualService`,
   `DpdService`, `InstalmentStatusService`, `DailyLoanJobs` orchestrator + `ScheduledDailyJobs`.
   Risk: penal base = overdue principal + overdue interest **after grace** on oldest-first overdue
   lines; test against a hand-computed example (acceptance test).

3. **Write-off two-person approval (test-first).** New domain `WriteOffRequest` (status
   REQUESTED/APPROVED/REJECTED). `POST /loans/{id}/write-off-requests` (admin A, reason required),
   `POST /write-off-requests/{id}/approve` (admin B≠A), `/reject`. Guards: DPD≥180 (config), distinct
   users, reason. On approve: `WriteOff` transition (exists) + publish `loan.written-off`. Migration
   `V0102`. Approver identity from `RequestContext`/JWT subject.

4. **ledger templates (test-first).** `PenalAccruedTemplate` (DR 1130 / CR 4003) and
   `WriteOffTemplate` (DR 5001 principal, DR 4001 interest-reversal? — per domain-rules: DR bad debt
   principal + reverse accrued interest/penal income, CR receivables 1100/1110/1130). Register in
   `PostingTemplateRegistry`; map in `LoanLedgerListener`. Test each balances.

5. **collections service (new module).** Mirror disbursement's hexagonal + Temporal shape. Domain:
   versioned `DunningPlan` (config, not hard-coded) with steps keyed by DPD threshold → action;
   `CollectionCase`, `CaseStatus`, `DunningAction`, signals. Temporal `DunningWorkflow`
   (`dun-{loanId}`) advancing on `DpdTick`; durable timers only for action retry/delivery. Consume
   `loan.dpd-updated`, `loan.repayment-allocated`, `mandate.debit-failed`, `loan.closed`,
   `loan.written-off`. `dunning_actions` unique `(case_id, step_id)` so replays/dupes never repeat.
   Publish collections.*. APIs: list/detail/note/assign/promise/escalate/resolve. Tests: plan logic
   (jqwik/unit), workflow scenarios + replay in-JVM, dedup.

6. **notification service (full).** Versioned templates (config) with placeholders; channels SMS,
   email, push, WhatsApp via simulators; dedup `(eventId, templateId, channel)`; retry w/ backoff;
   statuses QUEUED/SENT/FAILED/SUPPRESSED; consent + quiet-hours (21:00–08:00 business-calendar),
   held released in the morning. Subscribe to the 9 catalogued events. Migration for
   `notification_log` + `templates`. Tests: dedup, quiet-hours release, failing-channel→FAILED,
   placeholder render, no-PII.

7. **reconciliation in ledger (test-first matcher).** Simulators: `GET /sim/bank/statement?date=`,
   `GET /sim/rail/settlement-report?date=` + `reconDriftRate` knob. Pure `ReconMatcher`:
   classify each line MATCHED / MISSING_IN_LEDGER / MISSING_IN_BANK / AMOUNT_MISMATCH / DUPLICATE
   against 1001 postings by reference+amount. Persist `recon_runs`(extended)/`recon_items`
   (migration V0101). Publish `ledger.recon-completed` (reconType=BANK_RAIL + counts). Admin resolve
   (note) or raise adjusting entry via existing reversal mechanism.

8. **gateway routes** for collections (8107), write-off (loan-account), notifications (8109),
   reconciliation (ledger) + roles.

9. **admin-ui pages.** Collections queue (bucket cards, case table, filters, assign), case detail
   (dunning timeline + promise-to-pay), write-off requests (two-person visible), notification log,
   reconciliation results.

10. **E2E script** `scripts/e2e/phase4.sh` (3 loans over 12 months), k6, compose blocks for
    collections + notification (+ workflow profile), postgres-init DBs/roles, ADRs, README/runbook,
    backlog. Running it = Docker session.

## ADRs to record

- **0011 Collections dunning on DPD ticks** (time-design: business-calendar ticks drive dunning;
  durable timers only for retries/timeouts; `(caseId, stepId)` idempotency; signals).
- **0012 Reconciliation model** (bank/rail recon reusing `ledger.recon-completed` with a type
  discriminator + counts; matcher classification rules; recon_runs/recon_items schema; resolution
  via existing reversal).
- Write-off two-person control + penal base day-count if non-trivial → fold into an ADR if needed.

## Risks / open questions

- **Temporal in a second service (collections).** Reuse ADR 0008 wiring verbatim; prove in-JVM with
  `TestWorkflowEnvironment`. Live server = Docker.
- **`ledger.recon-completed` dual meaning** (trial-balance vs bank/rail). Resolved by additive type
  discriminator (ADR 0012), BACKWARD-safe. No new topic.
- **Quiet-hours uses business-calendar time, not wall clock** (phase prompt "Do not"). Hold/release
  is a function of business time; tested deterministically with an injected clock + calendar.
- **collections must not read loan tables** — it reacts to events only (ArchUnit/no JDBC to loans).
- Heavy infra stays deferred; every slice independently `./mvnw clean verify`-green.

## Definition of done (now)

`./mvnw clean verify` green incl. Spotless, enforcer, ArchUnit, 90% on lms-money/lms-schedule;
tests-first for penal accrual, DPD, write-off state, ledger templates, dunning plan/workflow,
notification dedup/quiet-hours, recon matcher; ADRs 0011/0012. Docker-session items written and
listed in README/runbook/backlog; `phase-4-done` withheld until they pass.
