# Phase 2 prompt (paste this whole file into Claude Code)

You are working in the `lms` repository on Phase 2.

1. Read CLAUDE.md, docs/architecture.md, docs/domain-rules.md and every ADR in docs/adr/.
2. Confirm the git tag phase-1-done exists and that `./mvnw clean verify` passes.
   If either fails, stop and tell me instead of continuing.
3. Write your plan to docs/plans/phase-2.md (tasks in order, risks, open questions),
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

## Phase 2: Loan core and ledger

**Goal:** an accepted offer becomes a loan with a schedule, balances and state history, every money movement is a balanced immutable ledger entry, and the business date can be moved forward for testing.

**Read first:** `CLAUDE.md`, all of `docs/domain-rules.md` (especially schedule engine, accrual, state machine, ledger invariants, posting templates). Write the plan to `docs/plans/phase-2.md`, then proceed.

**Ownership for this and later phases:** `loan-account` owns all balances and runs the daily jobs for interest accrual, penal accrual and DPD. `collections` consumes the resulting events and owns cases and dunning workflows. `docs/architecture.md` already says so; keep it accurate.

### Tasks

1. **Business calendar.** Add `BusinessCalendar` to `lms-config`. In dev, the business date is read from the Redis key `lms:platform:business-date`, falling back to today in `Asia/Kolkata`. Add an admin-only gateway endpoint (dev profile only) to set or advance it, and a `POST /admin/jobs/{name}/run?date=` endpoint on each service with scheduled jobs. All jobs use the calendar, never the system clock directly.
2. **`loan-account` service.**
    - Consume `application.accepted` idempotently (unique on `applicationId`). Create the loan in `Created`, attach the product version and accepted terms, draft the provisional schedule with `lms-schedule`, move to `PendingDisbursal`, publish `loan.created`. Loan numbers come from a database sequence, formatted like `LN-2026-000001`.
    - Implement the sealed `LoanState` and the transition function exactly as in `docs/domain-rules.md`. Persist every transition in `loan_state_history` with cause and event id.
    - Consume `disbursement.succeeded` (Phase 3 produces it). Generate the real schedule from the actual disbursement date, persist `schedule_items` (instalment number, due date, principal, interest, amounts paid per component, status `UPCOMING`, `DUE`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`), move to `Active`, publish `loan.activated`. Until Phase 3 exists, add a dev-profile endpoint `POST /admin/loans/{id}/simulate-disbursement` that emits the same internal handling.
    - Consume `repayment.received` (Phase 3 produces it), run the allocation waterfall from `docs/domain-rules.md` in one transaction, apply component amounts to schedule items, store `allocations` rows and publish `loan.repayment-allocated`. Build and test this now, with a dev-profile endpoint to inject `repayment.received`.
    - Daily interest accrual job per `docs/domain-rules.md`. Idempotent per `(loanId, businessDate)` through a unique constraint. Publish `loan.interest-accrued` (loanId, businessDate, amount, instalment number).
    - Close a loan when its outstanding total reaches zero: publish `loan.closed`.
    - Read APIs: list and filter loans, loan detail with balances (outstanding principal, accrued interest, penal, total due now, next due date), schedule, state history, and `GET /loans/{id}/foreclosure-quote?date=`.
3. **`ledger` service.**
    - Flyway migrations for `accounts` (seeded with the chart of accounts), `journal_entries` and `postings` (with `loan_id` and `customer_id` dimension columns). A deferred constraint trigger rejects any entry whose debits and credits differ at commit. A unique index on `source_event_id` makes posting idempotent. No `UPDATE` or `DELETE` is granted to the service role on these tables.
    - Posting templates as versioned Java classes selected by event type. Implement now: `disbursement.initiated`, `disbursement.succeeded`, `disbursement.failed`, and `loan.interest-accrued`. Leave repayment, penal and write-off templates for Phases 3 and 4, but define the template interface so they plug in.
    - Consume loan and disbursement events idempotently through `lms-outbox`. Publish `ledger.entry-posted`.
    - `POST /journal-entries/{id}/reverse` (admin only, reason required) creates a mirrored entry with `reversesEntryId`. Reversing twice is rejected.
    - Queries: entries with filters (loan, date, account), entry detail with postings, account balance as of a date, trial balance, and a per-loan sub-ledger.
    - A scheduled integrity job checks that the trial balance nets to zero. It exports the metric `ledger_trial_balance_diff_minor_units` and publishes an alert event if it is non-zero.
4. **Gateway routes** for loans and ledger, with read access for `analyst`.
5. **Admin UI pages.** Loans list and detail (state badge, balances, state history timeline). **EMI schedule viewer** (per-instalment status colours, paid components, overdue highlighting). **Ledger explorer** (entries, postings, account balances, trial balance with a zero-check badge, drill-down from a loan to its entries). Foreclosure quote dialog. A business date widget in the header, visible only in dev.
6. **E2E script** `scripts/e2e/phase2.sh`.

### Acceptance tests

- Duplicate delivery of `application.accepted` creates exactly one loan.
- After `simulate-disbursement`, the persisted schedule equals the `lms-schedule` output for the actual date. The `disbursement.succeeded` ledger entry balances and its principal-receivable posting equals the loan principal.
- **Ledger invariant property test (jqwik stateful):** apply random sequences of accrual days, allocations and reversals. After every step, assert that the trial balance is zero, no account that must be non-negative goes negative, and per-loan receivables equal the loan's balances.
- Constraint test: inserting an unbalanced journal entry directly in SQL fails at commit.
- Replay test: wipe the ledger database, replay `loan.events` and `disbursement.events` from offset 0, and compare. The trial balance and every account balance must be identical.
- Concurrency test: 50 concurrent events for one loan produce no lost updates and no duplicate postings.
- Accrual idempotency: running the accrual job twice for one date posts once. Over the life of a 12-month loan, total accrued interest equals total scheduled interest within 1 minor unit per instalment.
- State machine test: every illegal transition is rejected, audited and leaves the state unchanged.
- Reversing an entry restores the account balances. Reversing it again returns an error.

### Do not

- Do not store a mutable balance column in the ledger. Derive balances from postings.
- Do not grant `UPDATE` or `DELETE` on ledger tables.
- Do not use `BigDecimal` or `double` outside `lms-money`.
- Do not build real disbursement or repayment intake logic. Phase 3 does that.

### Exit checklist

- [ ] `./mvnw clean verify` green with the 90% coverage gate on `lms-money` and `lms-schedule`
- [ ] `scripts/e2e/phase2.sh` passes
- [ ] Replay test reproduces the ledger exactly
- [ ] Ledger explorer shows a balanced trial balance for a loan that has lived through 12 simulated months
- [ ] Tag `phase-2-done`
