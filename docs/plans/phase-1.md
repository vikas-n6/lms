# Phase 1 plan: Origination and decisioning

Status key: [ ] todo · [~] in progress · [x] done · (D) needs the Docker session to verify at runtime.

## Gate (done before any work)

- [x] Read CLAUDE.md, architecture.md, domain-rules.md, ADRs 0001–0004.
- [x] `phase-0-done` tag exists.
- [x] `./mvnw clean verify` green on JDK 25 (no Docker; unit + jqwik + ArchUnit + Spotless +
      enforcer + coverage all pass).

## Guiding constraints (from the locked decisions)

- No Docker on this machine (memory + ADR 0001): all container ITs, compose, the k6 run and the
  `scripts/e2e/phase1.sh` end-to-end run are **authored now, executed in the final Docker session**.
  Everything that can be proven with plain unit / property / ArchUnit / slice tests under
  `./mvnw clean verify` is proven now.
- Money & schedule maths live only in `lms-money` / `lms-schedule`; tests first; 90% line coverage.
- Decision never calls Customer/Origination over REST — the submit event carries a customer
  snapshot. Services talk through Kafka (outbox → inbox). Sync REST only via gateway or to simulators.
- Rule thresholds, bands, product params, currency are **versioned config/JSON**, never Java literals.
- No JPA/Lombok/ThreadLocal/BigDecimal-outside-money/`Instant.now()` in business code. `Clock`
  injected everywhere. Preview APIs only in `lms-concurrency`.

## Slices (in order). Each: tests first where money/schedule/state/transitions are involved,
## fast-loop the touched modules, then a conventional commit.

### S1 — `lms-schedule` platform module (money-critical core) [x]
- New pure-Java module `platform/lms-schedule`, depends only on `lms-money`. Register in parent
  `<modules>` and in `lms-bom`. Coverage gate 0.90.
- Types: `Product` interface + `FixedTenorReducingBalanceProduct` record (principal min/max/step,
  tenor set, APR, fee rate, GST rate, penal APR, grace days, foreclosure rate, currency, version);
  `ScheduleEngine`; `Schedule`/`ScheduleItem` records; `EmiQuote`/offer-quote helper (fee, GST, net
  disbursal); `effectiveAnnualRate()`; foreclosure-quote helper.
- Implement EMI, per-instalment split, last-instalment residue, due-date month-end clamp exactly per
  domain-rules "Schedule engine". `BusinessCalendar`-free (pure date maths on `LocalDate`).
- jqwik property tests for every principal/tenor/rate in range: principal portions sum to P, no
  negative balance, interest never negative, total interest within 1 minor unit/instalment of the
  closed form; EMI HALF_UP; net + fee + GST == principal. ADR for `effectiveAnnualRate` method
  (bisection on IRR) and the foreclosure simplification.

### S2 — contracts: new Avro events [x]
- Add `.avsc` for `customer.registered` (extend), `customer.kyc.completed`,
  `customer.bank-account.linked`, `application.submitted` (with customer snapshot), `.offered`,
  `.accepted`, `.rejected`, `.expired`, `decision.completed`. Keep the shared envelope. Serde
  round-trip unit tests. Schema-registry compatibility check is (D).

### S3 — `simulators` service [x]
- Spring Boot app, port 8190. Bureau (deterministic hash + persona prefixes), KYC (sync + async
  callback), Bank (penny-drop, statement-analysis), SMS (store + outbox read), Control API
  (`GET/PUT /sim/config/{sim}`, presets healthy/slow/flaky/down, `POST /sim/reset`, seed). In-memory
  stores; latency/failure/timeout/duplicate/callback-delay behaviours driven by config + seed.
- Unit/slice tests for determinism, persona overrides, preset application, outbox read.

### S4 — `customer` service extension [~]
- New fields (dob already, add id number, declared income, employment type). OTP (Redis TTL via SMS
  sim), KYC submit (Resilience4j around KYC sim), bank linking (penny-drop), append-only consents.
- Column-level AES-GCM encryption for id number + bank account number, keyed blind index for lookup.
  Publish the three customer events via outbox.
- Tests: crypto round-trip + blind-index determinism (unit), state/consent logic, web slice.

### S5 — `origination` service
- Application state machine as sealed type, exhaustive transitions (pure function, tests first).
- REST: create/update/submit/get/list(filter+page)/withdraw/accept. Submit guards (KYC verified,
  bank linked, consents) + customer snapshot + `application.submitted`.
- Consume `decision.completed`; APPROVE → build offer via `lms-schedule` (tenor options 3/6/9/12,
  EMI, fee, GST, net, APR), 48h expiry, scheduled `application.expired`. Accept → OTP e-sign →
  `application.accepted` with full terms. `CompliancePolicy` port (permissive impl) before offer and
  before accept, recorded.

### S6 — `decision` service
- Consume `application.submitted` idempotently. `Fanout.callAll` bureau + bank-statement (2s each).
- Rule DSL: sealed `Rule` + evaluator → `RuleResult`; versioned rule sets as JSON in `rule_sets`,
  Redis-cached by version. Implement the eligibility rules/outcomes/limit bands from domain-rules.
  Velocity = Redis sliding window (Lua). Persist full decision; publish `decision.completed`.
  Bureau unavailable after retries → REFER/`BUREAU_UNAVAILABLE`, never hang. REFER → review queue;
  `POST /decisions/{id}/review` (admin) overrides + re-publishes. `GET /decisions/{id}/explain`.
- Tests first for rules evaluator + limit bands + replay determinism (pure). Velocity/cache = (D) ITs.

### S7 — gateway routes + role mapping
- Routes for customers, applications, decisions, simulator control (admin only). Roles: admin all,
  analyst read, customer own records.

### S8 — notification stub
- Consume `application.*` + `customer.*`, log through SMS sim. Full service in Phase 4.

### S9 — admin UI pages
- Customers, Applications (+timeline), Decision explainer, Manual review queue, New-application
  wizard, Simulator control panel (+live SMS outbox). Build under `-Pui`.

### S10 — e2e + k6 + wiring
- `scripts/e2e/phase1.sh` (personas 9000/9002/9003/9004, velocity, bureau-down, duplicate event,
  replay). k6 decision-latency script (informational). compose service blocks, postgres-init roles,
  runbook + README updates. Running it all is (D).

## Risks / open questions
- `effectiveAnnualRate()`: domain-rules says compute from cash flows (IRR). No closed form → numeric
  (bisection). Record method + tolerance in an ADR. **Does not change any stored money amount** (it
  is a displayed figure), so no clarification needed.
- Resilience4j Boot-4 starter availability (ADR 0001 open risk) — first real outbound calls appear
  here; if only core modules resolve, wire them explicitly and record an ADR.
- AES-GCM column encryption + blind index is new; key from env, safe local default; no plaintext PII
  in logs/storage. Pure crypto helper is unit-testable without Docker.
- Heavy infra (Kafka/Redis/Postgres/Temporal) stays deferred; do not block the phase on it, but keep
  every slice independently build-green.

## Progress log (updated 2026-10-12)

All work below is committed and passes `./mvnw clean verify` on JDK 25 (unit + jqwik + ArchUnit +
Spotless + enforcer + coverage). No Docker on this machine, so every container/runtime piece is
authored-or-ported-as-port and executed in the final Docker session (memory + ADR 0001).

**Built and verified (pure domain / application / platform):**
- `lms-schedule`: product, reducing-balance engine, offer quotes, foreclosure; EMI/IRR/simple-
  interest in `lms-money`; jqwik properties. ADR 0005. `Money.times/min/max`,
  `Product.largestSupportedAtMost`.
- contracts: all Phase 1 Avro events + `MoneyAmount`; serde round-trips.
- `lms-security`: `FieldCipher` (AES-GCM) + `BlindIndex` (HMAC).
- origination: `ApplicationStateMachine` (sealed, exhaustive); `OfferPricing`; `CompliancePolicy`
  port + permissive impl; `SubmissionGuards`; `ApplicantSnapshotFactory` (ageYears via IST Clock).
- decision: `Rule` DSL + `DecisionEvaluator` + `LimitPolicy`; rule-set JSON loader + classpath repo
  + `rule-sets/v1.json`; `BureauBankFanout` (Fanout, ADR 0006); `DecisionInputsAssembler`.
- customer: `OtpService` + ports + `SecureRandomOtpGenerator`.
- simulators: full service (bureau/kyc/bank/sms + control API), in-memory, deterministic, tested.

**Not yet built / deferred to the Docker session (tracked in docs/backlog.md):**
- customer: aggregate extension (encrypted id/account columns via FieldCipher/BlindIndex, KYC via
  Resilience4j to the sim, bank linking/penny-drop, append-only consents), Redis `OtpStore`, the
  three outbox events, REST, JDBC, Flyway.
- origination: REST (create/update/submit/get/list/withdraw/accept), JDBC (`applications`,
  `application_events`, `offers`), decision.completed consumer, 48h expiry scheduled job + events,
  OTP e-sign accept, outbox publishing.
- decision: `application.submitted` consumer, HTTP bureau/bank clients (Resilience4j), Redis velocity
  sliding window (Lua), persistence (`decisions`, `bureau_snapshots`, `rule_sets`) + Redis rule-set
  cache, REST `/explain` + `/review`, `decision.completed` publishing.
- gateway routes + role mapping; notification stub; admin UI pages; `scripts/e2e/phase1.sh` + k6;
  compose service blocks + `JAVA_TOOL_OPTIONS` (incl. `--enable-preview` for decision, ADR 0006) +
  postgres-init roles; README/runbook.

## Definition of done for the phase (what can be checked now vs Docker)
- Now: `./mvnw clean verify` green; property/unit/arch tests for schedule, rules, state machine,
  crypto; Avro serde round-trips; ADRs for deviations.
- Docker session (D): e2e personas + failure scenarios, schema-registry compat, UI explainer +
  simulator panel, k6 latency. Tag `phase-1-done` only after those pass (mirrors Phase 0 discipline).
