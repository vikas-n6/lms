# ADR 0010: Disbursement saga — unknown-outcome, conflict and manual-review rules

- Status: Accepted
- Date: 2026-10-25

## Context

The disbursement payout runs as a Temporal workflow that must survive timeouts, duplicate and
out-of-order callbacks and crashes, and must never double-pay. The phase prompt is explicit: never
retry a payout blindly after an ambiguous result; three duplicate success callbacks yield exactly
one `disbursement.succeeded`; a success followed by a conflicting failure callback stays `SUCCEEDED`
and increments a conflict metric; a result that cannot be established moves to `MANUAL_REVIEW`.

## Decision

1. **One payout per attempt, idempotent key.** Each attempt calls the rail with a key tied to the
   disbursement and attempt (`disb-{id}-attempt-{n}`). The rail is idempotent on that key, so a
   retried POST — whether from a transport retry or a workflow retry — returns the same payout and
   never issues a second one.
2. **Unknown outcome ⇒ query, never re-pay.** If the payout call's response is lost/ambiguous, the
   rail client reports `responseLost`; the workflow then queries status by the idempotency key
   (bounded polling) rather than issuing another payout. Only a *definite* failure is retried (up to
   the configured maximum, exponential backoff via the pure `RetryPolicy`).
3. **Callback idempotency and conflict.** The callback adapter verifies the signature, dedups on
   `(railRef, status)`, then classifies against the persisted state with the pure
   `DisbursementStateMachine`: a first terminal result signals the workflow (`APPLIED`); the same
   terminal result again is ignored (`DUPLICATE`); a *different* terminal result after one is already
   recorded is ignored, logged and counted in the `lms.disbursement.callback.conflicts` metric
   (`CONFLICT`). So three duplicate success callbacks produce exactly one `disbursement.succeeded`,
   and a late failure after success leaves the disbursement `SUCCEEDED`.
4. **Manual review.** If neither the callback nor the bounded status polling establishes a terminal
   result, the workflow parks in `MANUAL_REVIEW` and awaits an admin signal (retry or cancel);
   retry starts a fresh attempt, cancel records `FAILED`.
5. **Determinism.** All side effects are activities; the workflow uses only `Workflow.sleep`/`await`
   for time/waiting, and structural changes are guarded with `Workflow.getVersion`. Proven by the
   `WorkflowReplayer` determinism test and the in-JVM `TestWorkflowEnvironment` scenario tests
   (ADR 0008).

## Consequences

- The "never double-pay" guarantee rests on the per-attempt idempotency key plus query-before-retry;
  both are tested in-JVM. The live crash test (kill mid-wait, restart, deliver the callback) is the
  Docker session's to run.
- `disbursement.failed` triggers loan-account to cancel the loan and the ledger to reverse the
  in-transit posting, so a failed payout nets to zero (verified end-to-end in the Docker session).
