# Phase 3 prompt (paste this whole file into Claude Code)

You are working in the `lms` repository on Phase 3.

1. Read CLAUDE.md, docs/architecture.md, docs/domain-rules.md and every ADR in docs/adr/.
2. Confirm the git tag phase-2-done exists and that `./mvnw clean verify` passes.
   If either fails, stop and tell me instead of continuing.
3. Write your plan to docs/plans/phase-3.md (tasks in order, risks, open questions),
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

## Phase 3: Money movement

**Goal:** a loan is disbursed through a durable Temporal saga that survives timeouts, duplicate and out-of-order callbacks and crashes. Repayments arrive through mandates, payment links and manual entry, are allocated by the waterfall, and can be reversed.

**Read first:** `CLAUDE.md`, `docs/domain-rules.md` (posting templates, allocation waterfall, idempotency), Phase 2 ADRs. Write the plan to `docs/plans/phase-3.md`, then proceed.

**Ownership for this phase:** `disbursement` owns the payout lifecycle. `repayment` owns the payment lifecycle (intake, mandates, idempotency, reversals). `loan-account` owns balances and runs the allocation waterfall, because it owns the schedule. This avoids stale copies of dues in another service.

### Tasks

1. **Simulators (extend).**
    - Rail: `POST /sim/rail/payout` (idempotent on `idempotencyKey`), `GET /sim/rail/payout?idempotencyKey=`, signed asynchronous callbacks (HMAC with a shared secret) delivering `SUCCESS` or `FAILED` with a UTR and failure code.
    - Mandate: `POST /sim/mandate/register`, `POST /sim/mandate/debit`, `DELETE /sim/mandate/{id}`, with asynchronous results.
    - Payments: `POST /sim/payments/create-link`, with a configurable auto-completion callback.
    - New control knobs: `unknownOutcomeRate` (the operation succeeds but the response is lost or times out), `outOfOrderRate`, `returnRate` (a debit that succeeded is later returned), alongside the existing latency, failure, duplicate and delay settings.
2. **`disbursement` service with Temporal.**
    - Consume `loan.created` and start `DisbursementWorkflow` with workflow id `disb-{loanId}` (a duplicate start is a no-op).
    - Workflow steps: compliance check (the `CompliancePolicy` mock), record `INITIATED` and publish `disbursement.initiated`, call the rail with an idempotency key tied to the disbursement, then wait for the callback signal or a timer (default 30 minutes, configurable, 10 seconds in tests).
    - **Unknown outcome rule:** if the rail call times out or errors ambiguously, query payout status by idempotency key. Never issue a second payout blindly.
    - On timer expiry, poll status with backoff for a bounded number of rounds, then move to `MANUAL_REVIEW`.
    - On success: record `SUCCEEDED` with UTR, publish `disbursement.succeeded` (net amount, fee, GST, disbursal date).
    - On a retryable failure: up to 3 attempts with exponential backoff, each attempt recorded in `payout_attempts`. On final failure: record `FAILED`, publish `disbursement.failed`. Ledger reverses in-transit and `loan-account` cancels the loan (consume `disbursement.failed`).
    - `POST /callbacks/rail` verifies the signature, deduplicates on `(railRef, status)` with Redis plus a unique constraint, signals the workflow, and acknowledges quickly. Duplicates return 200 and do nothing. A conflicting second terminal status is ignored, logged, and counted in a metric and alert.
    - Admin endpoints: list and detail, retry from `MANUAL_REVIEW`, cancel, and a link to the Temporal UI for the workflow.
    - Workflow code must be deterministic. Use `Workflow.getVersion` for changes. Temporal Java SDK 1.37.0 and later has stable virtual-thread support (`WorkerOptions.setUsingVirtualThreads` for activities, `WorkerFactoryOptions.setUsingVirtualWorkflowThreads` for workflow threads). Enable both after the Java 25 smoke test from ADR 0001 passes, and record the result in an ADR.
3. **`repayment` service (no Temporal).**
    - Mandates: register for a loan, track status, cancel. Publish `mandate.registered`.
    - Auto-debit job (via the business calendar and the job-run endpoint): for loans with an instalment due on the business date and an active mandate, call the mandate debit. A failed debit publishes `mandate.debit-failed` and is re-presented once two business days later.
    - Customer-initiated payment: `POST /repayments` (amount, channel, optional `purpose=FORECLOSURE` with a quote id) returns a payment link from the simulator.
    - Admin manual receipt: `POST /repayments/manual` with a bank reference.
    - `POST /callbacks/payments` and `POST /callbacks/mandates` (signed). On confirmed funds, persist the repayment and publish `repayment.received`.
    - A post-success return callback publishes `repayment.reversed`.
    - Idempotency on client calls (`Idempotency-Key`) and on callbacks (provider reference plus status).
4. **`loan-account` (extend).**
    - Consume `repayment.received`. Run the waterfall exactly as in `docs/domain-rules.md` inside one transaction: update schedule items, store `allocations` rows, publish `loan.repayment-allocated` (components, instalments touched, advance remaining). A foreclosure payment at least equal to the quote closes the loan as `Foreclosed`.
    - Consume `repayment.reversed`. Undo the allocation with new reversing records (never edits), reopen the affected instalments, publish `loan.repayment-reversed`.
    - Keep the Phase 2 dev injection endpoints behind the `dev` profile only.
5. **`ledger` (extend).** Add templates: `repayment.received`, `loan.repayment-allocated`, `repayment.reversed` / `loan.repayment-reversed` (reverses the receipt and the allocation entries), overpayment refund.
6. **Gateway routes** for disbursements, repayments, mandates and the callback endpoints (callbacks bypass JWT but require the signature).
7. **Admin UI pages.** **Disbursement monitor** (list, status, attempts, Temporal link, retry and cancel). Repayments (list, detail with the allocation breakdown). Mandates (status and debit history). A manual collection dialog. The simulator panel gains rail, mandate and payments controls.
8. **E2E script** `scripts/e2e/phase3.sh` covering the scenarios below.

### Acceptance tests

- Happy path: accepted offer to `Active` loan. Ledger shows the in-transit entry and the success entry, account 2300 returns to zero, fee income and GST are posted, and bank is reduced by the net amount.
- Unknown outcome: the rail processes the payout but the response is lost. The workflow queries status and completes. The rail simulator records exactly one payout for the key.
- Three duplicate success callbacks produce exactly one `disbursement.succeeded`.
- A callback delayed beyond the timer is resolved by status polling.
- Rail fails three times: `disbursement.failed`, in-transit reversed to a net-zero effect, loan `Cancelled`.
- Success followed by a conflicting failure callback leaves the disbursement `SUCCEEDED` and increments the conflict metric.
- **Crash test:** kill `disbursement` while a workflow waits for a callback, restart it, deliver the callback, and see the workflow finish.
- Workflow replay test with `WorkflowReplayer` on recorded histories to prove determinism.
- Waterfall: a payment covering penal, interest and part of principal on the oldest overdue instalment allocates in the documented order. A larger payment clears overdue items, then the current instalment, then leaves an advance in suspense. Allocated plus remaining always equals the payment (property test).
- A duplicate payment callback produces one allocation.
- A returned debit restores the instalment and the ledger balances exactly to their pre-payment state.
- Foreclosure: paying the quoted amount closes the loan as `Foreclosed`, with all balances at zero.
- k6 test for `POST /callbacks/payments`: p95 acknowledgement below 200 ms at 500 requests per second (informational in this phase).

### Do not

- Do not use Temporal anywhere except `disbursement` (and later `collections`).
- Do not retry a payout blindly after an ambiguous result.
- Do not accept any callback without a valid signature.
- Do not let `repayment` write to loan tables or read the loan database.
- Do not mutate or delete allocation or ledger rows to correct them. Add reversing records.

### Exit checklist

- [ ] `./mvnw clean verify` green
- [ ] `scripts/e2e/phase3.sh` passes, including the crash and unknown-outcome cases
- [ ] Ledger trial balance is zero after all scenarios
- [ ] Disbursement monitor, repayment and mandate pages work in the UI
- [ ] Tag `phase-3-done`
