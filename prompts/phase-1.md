# Phase 1 prompt (paste this whole file into Claude Code)

You are working in the `lms` repository on Phase 1.

1. Read CLAUDE.md, docs/architecture.md, docs/domain-rules.md and every ADR in docs/adr/.
2. Confirm the git tag phase-0-done exists and that `./mvnw clean verify` passes.
   If either fails, stop and tell me instead of continuing.
3. Write your plan to docs/plans/phase-1.md (tasks in order, risks, open questions),
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

## Phase 1: Origination and decisioning

**Goal:** a customer can register, verify, link a bank account, apply, get an explainable decision and accept an offer, entirely through events and simulators, with a back-office UI to watch and steer it.

**Read first:** `CLAUDE.md`, all of `docs/domain-rules.md` (product, schedule engine, eligibility rules, idempotency), and the Phase 0 ADRs. Write the plan to `docs/plans/phase-1.md`, then proceed.

### Tasks

1. **`lms-schedule` platform module (new).** Pure Java library: `Product` interface, `FixedTenorReducingBalanceProduct`, `ScheduleEngine` producing `Schedule` and `ScheduleItem` records, `effectiveAnnualRate()`, and foreclosure quote helpers. It depends only on `lms-money`. Implement exactly the rules in `docs/domain-rules.md` with jqwik property tests. Origination uses it for quotes now; loan-account uses it in Phase 2. Add the module to the repo layout and BOM.
2. **`simulators` service.** One Spring Boot app exposing:
    - Bureau: `POST /sim/bureau/pull` returns score (300 to 900), active accounts and overdue flag. The score is derived deterministically from a hash of the id, with persona overrides by phone prefix: `9000` approve-high, `9001` approve-mid, `9002` reject-low-score, `9003` refer-band, `9004` bureau-delinquent.
    - KYC: `POST /sim/kyc/verify` returns `VERIFIED`, `FAILED` or `PENDING`, with an asynchronous callback to `customer` for `PENDING`.
    - Bank: `POST /sim/bank/penny-drop` and `POST /sim/bank/statement-analysis` (estimated monthly income).
    - SMS: `POST /sim/sms/send` stores the message. `GET /sim/sms/outbox` lets tests and the UI read OTPs.
    - Control API: `GET` and `PUT /sim/config/{simulator}` with `latencyMsMin`, `latencyMsMax`, `failureRate`, `timeoutRate`, `duplicateCallbackRate`, `callbackDelayMs`, and a random `seed` for reproducible runs. `POST /sim/reset`. Named presets: `healthy`, `slow`, `flaky`, `down`.
3. **`customer` service (extend).** Fields: phone, name, date of birth, id number, declared monthly income, employment type. Flows: registration, OTP verification (OTP in Redis with a TTL, sent through the SMS simulator), KYC submission through the KYC simulator behind Resilience4j, bank account linking with a penny-drop check, append-only consent records (data processing, credit pull, loan agreement). Encrypt id number and bank account number at column level (AES-GCM, key from environment) with a keyed blind index for lookups. Publish `customer.registered`, `customer.kyc.completed`, `customer.bank-account.linked`.
4. **`origination` service.**
    - Application state machine: `DRAFT`, `SUBMITTED`, `DECISIONED`, `OFFERED`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `WITHDRAWN` as a sealed type with exhaustive transitions.
    - Endpoints: create draft, update, submit, get, list (filters and paging), withdraw, accept offer.
    - Submit requires verified KYC, a linked bank account and all consents. It stores a customer snapshot and publishes `application.submitted`. The snapshot travels in the event so Decision never calls Customer synchronously.
    - On `decision.completed` with `APPROVE`, build an offer using `lms-schedule` (amount, tenor options of 3, 6, 9 and 12 months, EMI, fee, GST, net disbursal, effective APR). Offers expire after 48 hours (default). A scheduled job emits `application.expired`.
    - Accepting an offer needs an OTP e-sign (SMS simulator), then publishes `application.accepted` containing the full accepted terms.
    - `CompliancePolicy` port with a permissive implementation. Call it before offer creation and before acceptance, and record each result.
5. **`decision` service.**
    - Consume `application.submitted` idempotently. Fan out bureau pull and bank-statement analysis in parallel with `Fanout.callAll` (timeout 2 seconds each).
    - Rule set as a typed Java DSL: a sealed `Rule` interface and an evaluator returning a `RuleResult` per rule. Rule sets are versioned, stored as JSON in `rule_sets`, and loaded through a Redis cache keyed by version.
    - Implement the eligibility rules, outcomes and limit bands from `docs/domain-rules.md`. Add the velocity check as a Redis sliding window (Lua script).
    - Persist every decision with the inputs snapshot, bureau snapshot, rule set version, each rule's result, reason codes, outcome and offered limit. Publish `decision.completed`.
    - If the bureau is unavailable after the retries, the outcome is `REFER` with reason `BUREAU_UNAVAILABLE`. Applications must never hang.
    - `REFER` decisions enter a manual review queue. `POST /decisions/{id}/review` (admin only) records an override with a reason, and publishes a new `decision.completed`.
    - `GET /decisions/{id}/explain` returns a human-readable breakdown.
6. **Gateway.** Routes for customers, applications, decisions and simulator control (admin only). Role mapping: `admin` can do everything, `analyst` can read, `customer` can act on their own records.
7. **Admin UI pages.** Customers (list, detail, KYC and bank status, consents). Applications (list with filters, detail with event timeline). **Decision explainer** (inputs, bureau snapshot, rule-by-rule result, reason codes, rule set version). Manual review queue. New application wizard (back-office assisted). **Simulator control panel** (per-simulator sliders and presets, live SMS outbox view).
8. **Notification stub.** Consume `application.*` and `customer.*` events and log them through the SMS simulator. The full service arrives in Phase 4.
9. **E2E script** `scripts/e2e/phase1.sh` covering the personas and failure scenarios below.

### Acceptance tests

- Happy path: register, OTP, KYC, link bank, apply with persona `9000`, receive an approved offer, accept it with OTP. `application.accepted` appears on Kafka with the correct terms.
- Persona `9002` gets `REJECT` with `BUREAU_SCORE_LOW`. Persona `9003` gets `REFER`, an admin overrides it, and the override produces an offer. Persona `9004` gets `REJECT` with `BUREAU_DELINQUENT`.
- Four applications from one phone within 24 hours: the fourth is rejected with `VELOCITY_BREACH`.
- Bureau simulator set to 100% failure: the decision still completes within 10 seconds as `REFER` with `BUREAU_UNAVAILABLE`.
- The same `application.submitted` event delivered twice produces exactly one decision.
- Replay test: re-evaluating a stored decision's inputs against its stored rule set version gives an identical outcome.
- Offer figures from `lms-schedule` match the property-tested engine. An offer's net disbursal plus fee plus GST equals the principal.
- Decision latency under the `healthy` preset: p95 below 3 seconds at 20 concurrent applications (k6 script, informational in this phase).

### Do not

- Do not let Decision call Customer or Origination over REST.
- Do not store the id number or bank account number in plain text, and do not log them.
- Do not build loan accounts, ledger or disbursement logic yet.
- Do not hard-code rule thresholds in Java; they come from the versioned rule set JSON.

### Exit checklist

- [ ] `./mvnw clean verify` green
- [ ] `scripts/e2e/phase1.sh` passes all persona and failure scenarios
- [ ] Decision explainer and simulator panel work in the UI
- [ ] All new events registered in Schema Registry and compatibility check passes
- [ ] ADRs written for any rule or design deviation
- [ ] Tag `phase-1-done`
