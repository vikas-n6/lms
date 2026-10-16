# Phase 3 plan: Money movement

Status key: [ ] todo · [~] in progress · [x] done · (D) needs the Docker session to verify at runtime.

## Gate (done before any work)

- [x] Read CLAUDE.md, architecture.md, domain-rules.md, ADRs 0001–0007.
- [x] `phase-2-done` tag exists. It was missing at session start (only `phase-0/1-done`), and two
      Phase-2 append-only trigger migrations were uncommitted. On the user's instruction I committed
      those (`feat(ledger,loan-account): append-only triggers …`) and tagged `phase-2-done`,
      matching the Phase 0/1 "unblock sequencing" style — the container/e2e verification Phase 2
      deferred stays deferred to the final Docker session.
- [x] `./mvnw clean verify` green on JDK 25 (exit 0): Spotless, enforcer, ArchUnit, coverage, jqwik,
      Avro serde via MockSchemaRegistry. No Docker.

## Guiding constraints (locked decisions that shape every slice)

- **No Docker on this machine** (memory + ADR 0001): all Testcontainers ITs, the live Temporal dev
  server, Kafka, Redis, HTTP callbacks over the wire, docker compose, `scripts/e2e/phase3.sh`, the
  crash/unknown-outcome/replay ITs against a real broker, the k6 test and the UI-with-OIDC run are
  **authored now, executed in the final Docker session**. Everything provable with pure unit /
  property (jqwik) / slice / ArchUnit tests — **including Temporal's in-JVM `TestWorkflowEnvironment`**
  (time-skipping, no external process) — is proven now and must stay green every commit.
- **No money-maths changes anywhere.** Allocation waterfall, posting amounts, foreclosure quote and
  the net-amount identity all already live in `lms-money` / `lms-schedule` / loan-account (Phase 2,
  property-tested). Phase 3 only *composes* those results into new ledger templates and moves money
  between services via events. No new `BigDecimal` outside `lms-money` (ArchUnit enforces). This is
  why no slice needs a money-handling clarification.
- **Ownership (phase prompt).** `disbursement` owns the payout lifecycle (Temporal). `repayment`
  owns the payment lifecycle (intake, mandates, idempotency, reversals) and **never writes to or
  reads the loan database**. `loan-account` owns balances and runs the allocation waterfall.
- **Temporal only in `disbursement`** this phase (and later `collections`). No global orchestrator.
- Outbox for every publish, inbox for every consume (idempotent on `eventId`; ledger also on
  `sourceEventId`). Per-aggregate Kafka key = loanId so ordering holds per loan.
- **Every callback is signed** (HMAC) and verified before any effect; a callback with no/invalid
  signature is rejected. Callbacks bypass JWT at the gateway but carry the signature.
- **Never re-pay after an ambiguous result**: query payout status by idempotency key first.
- **Never mutate/delete allocation or ledger rows**: corrections are new reversing records.

## Slices (in order). Each: tests first for money/ledger/state/saga, fast-loop the touched modules,
## then a conventional commit.

### S1 — contracts: new Avro events [ ]
Add `.avsc` (shared `EventMetadata` envelope + `MoneyAmount`) for the Phase 3 producers that don't
exist yet: `repayment.failed` (RepaymentFailed), `repayment.reversed` (RepaymentReversed),
`mandate.registered` (MandateRegistered), `mandate.debit-failed` (MandateDebitFailed),
`loan.repayment-reversed` (LoanRepaymentReversed), `loan.foreclosed` (LoanForeclosed). Add optional,
defaulted `purpose` (nullable string) + `foreclosureQuoteId` (nullable string) to `RepaymentReceived`
— a BACKWARD-compatible addition (defaults null), not a money change (amount/allocation unchanged).
Serde round-trip unit tests (MockSchemaRegistry). Schema BACKWARD compat check = (D).

### S2 — `lms-security`: signed-callback helper [ ]
`CallbackSigner` (HMAC-SHA256 over a canonical `timestamp + "." + rawBody`, shared secret from config)
and `CallbackVerifier` (constant-time compare, configurable clock-skew window using the injected
`Clock`). JDK `javax.crypto.Mac` — **no new dependency**. Pure, tests first: sign→verify round-trips;
tampered body / wrong secret / stale timestamp rejected. Used by simulators to sign and by
disbursement/repayment to verify. ADR for the scheme.

### S3 — simulators: knobs + rail / mandate / payments [ ]
- Extend `SimulatorConfig` + `SimulatorPreset` + `SimBehavior` with `unknownOutcomeRate` (operation
  succeeds but the response is lost/times out), `outOfOrderRate`, `returnRate` (a succeeded debit is
  later returned), keeping existing latency/failure/duplicate/delay. Add `rail`, `mandate`,
  `payments` to `SimConfigRegistry.SIMULATORS`; extend `ControlController` DTOs.
- **Rail**: `POST /sim/rail/payout` (idempotent on `idempotencyKey` — one stored payout per key,
  returns the same record on replay), `GET /sim/rail/payout?idempotencyKey=`, async **signed**
  callback `SUCCESS|FAILED` with UTR + failure code; `unknownOutcomeRate` stores the payout but
  loses/stalls the HTTP response; `outOfOrderRate`/`duplicateCallbackRate` reorder/duplicate
  callbacks.
- **Mandate**: `POST /sim/mandate/register`, `POST /sim/mandate/debit`, `DELETE /sim/mandate/{id}`,
  async signed results; `returnRate` schedules a post-success return callback.
- **Payments**: `POST /sim/payments/create-link` with a configurable auto-completion callback.
- Tests now: idempotent payout store (same key → same record, one payout), outcome decision, knob
  parsing. Wire-delivery of callbacks = (D). Callback HTTP clients excluded from coverage like the
  existing `HttpKycCallbackClient`.

### S4 — `disbursement` service (new module) + Temporal [ ]
New Maven module `services/disbursement` (port 8105), hexagonal.
- **domain (pure, tests first):** `DisbursementState` sealed (`Initiated`, `Succeeded`, `Failed`,
  `ManualReview`), `Disbursement` aggregate, `PayoutAttempt`, retry/backoff policy (bounded), the
  callback-conflict rule (a second *conflicting* terminal status is ignored). Exhaustive `switch`,
  no `default`.
- **application:** ports (RailClient, DisbursementRepository, PayoutAttemptRepository, event
  publisher, WorkflowStarter, StatusQuery) + services (StartDisbursement, ApplyRailResult,
  RetryFromManualReview, CancelDisbursement, query).
- **Temporal:** `DisbursementWorkflow` (id `disb-{loanId}`, duplicate start = no-op via
  `WorkflowIdReusePolicy`/already-started), activities for compliance check (`CompliancePolicy`
  mock), record+publish `disbursement.initiated`, rail call with a disbursement-tied idempotency key,
  wait for the callback **signal** or a timer (default 30m, **10s in tests**). Unknown outcome →
  query status by key, never a blind second payout. Timer expiry → bounded status polling with
  backoff → `MANUAL_REVIEW`. Success → `SUCCEEDED` + UTR + `disbursement.succeeded` (net, fee, GST,
  date). Retryable failure → 3 attempts exp backoff, each in `payout_attempts`; final → `FAILED` +
  `disbursement.failed`. Deterministic; `Workflow.getVersion` for changes.
- **adapters.in:** Kafka `loan.created` listener → start workflow (idempotent start); REST
  `POST /callbacks/rail` (verify signature → dedup `(railRef,status)` via Redis + unique constraint →
  signal workflow → fast 200; duplicate → 200 no-op; conflicting terminal → ignore, log, metric);
  admin REST (list, detail, retry, cancel, Temporal-UI link).
- **adapters.out:** JDBC `disbursements` / `payout_attempts` (Flyway V0100, created/updated/version,
  money as bigint + currency), outbox publisher for `disbursement.events`, Resilience4j-wrapped rail
  HTTP client, Temporal client/worker config (virtual-thread options behind the ADR-0001 smoke test).
- **Tests now:** domain state machine + conflict rule; saga via **`TestWorkflowEnvironment`**
  (in-JVM, time-skipping): happy path, callback-after-timer resolved by status poll, three duplicate
  success callbacks → one `disbursement.succeeded`, unknown outcome → status query completes & exactly
  one payout, three rail failures → `disbursement.failed`, success-then-conflicting-failure stays
  `SUCCEEDED` + conflict metric, **`WorkflowReplayer`** determinism on a recorded history. The live
  crash test and the broker/Redis-backed callback dedup = (D). ArchUnit (hexagonal, no
  ThreadLocal/BigDecimal).

### S5 — `repayment` service (new module, no Temporal) [ ]
New Maven module `services/repayment` (port 8106), hexagonal. **No loan-DB access.**
- **domain (pure, tests first):** mandate lifecycle state, re-presentation rule (a failed debit is
  re-presented once, two business days later), idempotency decision, channel/purpose types.
- **mandates:** register (publish `mandate.registered`), status, cancel.
- **auto-debit job** (business calendar + `POST /admin/jobs/auto-debit/run?date=`): loans with an
  instalment due on the business date and an active mandate → call mandate debit; failed →
  `mandate.debit-failed` + schedule the single re-presentation.
- **customer payment** `POST /repayments` (amount, channel, optional `purpose=FORECLOSURE` +
  `quoteId`) → payment link from the payments simulator. **admin manual receipt**
  `POST /repayments/manual` with a bank reference.
- **callbacks** `POST /callbacks/payments`, `POST /callbacks/mandates` (signed): on confirmed funds
  persist the repayment + publish `repayment.received` (carrying `purpose`/`quoteId` when a
  foreclosure); a post-success return publishes `repayment.reversed`.
- **idempotency:** client `Idempotency-Key` (Redis + Postgres unique) and callback (provider ref +
  status).
- **persistence:** `repayments`, `mandates`, `payment_attempts` (Flyway V0100). Which loans/instalments
  are "due" comes from loan-account **via the gateway** (synchronous read is gateway-mediated), never
  the loan DB. Tests now: re-presentation scheduling, idempotency, web slices with fakes; runtime
  callbacks/debit = (D). ArchUnit.

### S6 — `loan-account` extensions [ ]
- Consume `repayment.reversed`: a new `ReverseAllocationService` writes **reversing** allocation rows
  (never edits the append-only `allocations`), decrements the mutable `schedule_items` paid
  components, reopens the affected instalments, and publishes `loan.repayment-reversed`. Flyway V0101
  adds a `reversal_of_repayment_id` column to `allocations` for the reversing rows.
- **Foreclosure close:** when `repayment.received` carries `purpose=FORECLOSURE` and the amount is at
  least the foreclosure quote at the business date, allocate then transition `Active|Delinquent →
  Foreclosed` and publish `loan.foreclosed`. Extend `LoanStateMachine`/`LoanTransition` only if the
  Foreclosed path isn't already present (verify first; add tests either way).
- Ensure Phase-2 dev injection endpoints are `@Profile("dev")` only.
- Tests first: reversal restores pre-payment schedule state exactly; foreclosure-close transition
  (legal + guard-failing). 

### S7 — `ledger` extensions [ ]
Add templates (balanced, tests first) and register in `PostingTemplateRegistry.defaults()`:
`repayment.received` (DR 1001 / CR 2200), `loan.repayment-allocated` (DR 2200 total / CR 1130 penal,
1110 interest, 1100 principal), `repayment.reversed` + `loan.repayment-reversed` (reverse the receipt
and the allocation), overpayment refund (DR 2200 / CR 1001). Extend the Kafka listeners to route
these event types. Unit tests per template (balances, per-currency), registry test.

### S8 — gateway routes [ ]
Routes + roles for `/disbursements/**`, `/repayments/**`, `/mandates/**` and the callback endpoints
(`/callbacks/**` bypass JWT but the downstream service requires the signature). Admin vs analyst
roles for retry/cancel/jobs.

### S9 — admin UI [ ] (build under `-Pui`; OIDC login = D)
Disbursement monitor (list, status, attempts, Temporal link, retry/cancel). Repayments (list, detail
with allocation breakdown). Mandates (status + debit history). Manual-collection dialog. Simulator
panel gains rail/mandate/payments controls.

### S10 — e2e + wiring [ ]
`scripts/e2e/phase3.sh` covering the acceptance scenarios (happy path, unknown outcome, 3 duplicate
callbacks, delayed callback resolved by polling, 3 rail failures → cancelled, conflicting callback,
crash test, waterfall, duplicate payment callback, returned debit, foreclosure). compose blocks for
`disbursement` (+ `workflow` Temporal profile) and `repayment`, postgres-init DBs/roles, k6 script for
`POST /callbacks/payments`. runbook + README updates. Running it = (D).

## ADRs to record
- **0008 Temporal on JDK 25 & virtual-thread options** — result of the in-JVM `TestWorkflowEnvironment`
  smoke test (ADR 0001's least-verified item); decide whether `setUsingVirtualThreads` /
  `setUsingVirtualWorkflowThreads` are enabled now or deferred to the live-server Docker check.
- **0009 Signed callbacks** — HMAC-SHA256 scheme, canonicalization, timestamp-skew window, shared
  secret via config/env, where verification lives.
- **0010 Saga unknown-outcome rule** — query-status-by-idempotency-key before any retry; the
  `MANUAL_REVIEW` terminal-for-humans state; conflicting-terminal-callback handling + metric.
- (If non-trivial) foreclosure `purpose` on `repayment.received` and the Foreclosed close path;
  failed-debit single re-presentation after two business days.

## Risks / open questions
- **Temporal SDK on JDK 25** (ADR 0001 least-verified). Plan: prove it now via the in-JVM
  `TestWorkflowEnvironment` (no Docker). If the SDK won't load/run on JDK 25, test the pure
  activity/decision logic now and defer workflow+replay ITs to the live-server Docker session,
  recording the finding in ADR 0008. *No money-handling or locked-decision change either way.*
- **Resilience4j Boot 4 starter** (ADR 0001 open risk) — Phase 3 is the first phase with real
  outbound calls (rail/mandate/payments). If only a Boot-3 starter exists, wire the core modules
  explicitly and record a follow-up ADR. Resilience4j config values (attempts, backoff, timeouts,
  bulkhead) come from configuration, never hard-coded.
- **`RepaymentReceived` gains `purpose`/`foreclosureQuoteId`** — backward-compatible Avro addition
  (defaults null). It routes a state transition, not a money amount, so it needs no money-handling
  clarification; recorded in the ADR if it turns out non-trivial.
- **repayment ↔ loan-account read** must stay gateway-mediated (which instalments are due); no
  cross-service DB access, no direct service-to-service REST.
- Heavy infra (Temporal server, Kafka, Redis, Postgres, HTTP callbacks, compose, e2e, UI, k6) stays
  deferred; keep every slice independently build-green under `./mvnw clean verify`.

## Definition of done (now vs Docker)
- **Now:** `./mvnw clean verify` green; tests-first for the disbursement saga (via
  `TestWorkflowEnvironment` + `WorkflowReplayer`), callback signing, ledger templates, reversal and
  foreclosure transitions, idempotency, re-presentation; Avro serde round-trips; ADRs for deviations;
  90% coverage holds on `lms-money`/`lms-schedule` and the schedule engine.
- **Docker session (D):** `scripts/e2e/phase3.sh` incl. the crash and unknown-outcome cases against a
  live broker + Temporal dev server; broker/Redis-backed callback dedup; schema BACKWARD compat; the
  k6 p95 check; UI with OIDC. Tag `phase-3-done` after those pass (mirroring the prior phases).
