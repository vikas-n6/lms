# Backlog

Tracked deferrals and TODOs. Every `TODO` in code must have a matching line here.

> **Decision (Phase 0):** this machine has no Docker. Every container-dependent item below is
> **authored but not executed**, and execution is **batched into one session after all phases are
> built** — not run per phase. Phase tags are created when the Docker-independent work is green, with
> the container DoD items annotated as deferred.

## Deferred: needs a container runtime (Docker/Compose) — run after all phases

These are authored but cannot run on this machine (no Docker). Run them in the final Docker session
and record results (ADR 0001 for the smoke tests). This list grows per phase.

- [ ] ADR 0001 smoke tests: Kafka 4.3.1 broker + clients on JDK 25 (Testcontainers).
- [ ] ADR 0001 smoke tests: Confluent Schema Registry 8.3.2 + Avro 1.12.2 serde round-trip.
- [ ] ADR 0001 smoke tests: Temporal SDK 1.40.0 workflow + activity on JDK 25 with both
      virtual-thread options enabled (least-verified item).
- [ ] ADR 0001 smoke tests: jOOQ 3.21.9, Redisson 4.8.0, Resilience4j 2.4.0, Testcontainers 2.0.5.
- [ ] Run all `*IT` Testcontainers tests under Failsafe (`lms-testkit` bases, outbox Kafka-restart
      IT, idempotency IT).
- [ ] `docker compose --profile full up` brings every container to healthy.
- [ ] `scripts/e2e/phase0.sh` incl. the Kafka kill/restart exactly-once delivery scenario.
- [ ] Admin UI OIDC login against Keycloak and the Status page showing all services.
- [ ] Tag `phase-0-done` once the above pass.

## Phase 1: remaining service wiring + e2e (Docker session)

The Phase 1 domain/application/platform cores are built and `./mvnw clean verify`-green (see
docs/plans/phase-1.md "Progress log"). These remaining pieces are container-coupled or need a running
stack, so they are authored/wired and verified in the Docker session.

All the service wiring, gateway, notification stub, admin UI, e2e + k6 scripts and compose blocks
below are now **authored and `./mvnw clean verify`-green**; what remains is to **run** them against a
real stack and settle the container-only items.

- [x] customer: KYC (encrypted id number + blind index, Resilience4j, async callback), bank linking
      (penny-drop + encrypted account number), append-only consents, Redis `OtpStore`, SimSmsSender,
      the two outbox events, REST + JDBC + Flyway V0101 — authored.
- [x] origination: Application aggregate, REST (create/get/list/submit/withdraw/esign/accept), JDBC
      (applications/application_events/customer_projection), decision.completed + customer.events
      listeners, 48h expiry job, outbox for all `application.*`, compliance recorded — authored.
- [x] decision: application.submitted consumer (inbox), HTTP bureau/bank clients (Resilience4j core),
      JDBC (decisions/bureau_snapshots), rule-set JSON repo, REST `/explain` + `/review` +
      `/review-queue`, decision.completed publishing, `--enable-preview` in the image (ADR 0006) —
      authored.
- [x] gateway routes + role mapping; notification stub; admin UI (all pages); `scripts/e2e/phase1.sh`;
      `scripts/k6/phase1-decision-latency.js`; compose service blocks — authored.
- [ ] RUN in the Docker session: `scripts/e2e/phase1.sh` (all persona + failure scenarios) and the k6
      latency script; duplicate-event exactly-once and replay at the Kafka level.
- [ ] decision: swap the in-memory velocity window for the Redis sliding-window Lua script; add the
      Redis-cached JDBC `rule_sets` repository (classpath repo serves dev now).
- [ ] Schema Registry BACKWARD compatibility check for the new Avro events (`scripts/check-schema-compat.sh`).
- [ ] origination: encrypt phone/DOB at rest (currently plaintext columns; id number + account number
      are already encrypted in customer); add a read endpoint for customer KYC/bank status (UI notes it).
- [ ] README + runbook: document starting/testing the Phase 1 stack.
- [ ] Tag `phase-1-done` once the above pass (mirrors the Phase 0 discipline).

## Deferred: Money / serde round-trip property

- [ ] The "round-trips through the database and JSON are lossless" money property (domain-rules.md
      "Money type") is currently proven at the value level in `lms-money`. Add the DB round-trip in
      the `customer`/ledger persistence layer and the JSON round-trip in `lms-events` serde.

## Open risks to settle (from ADR 0001)

- [ ] Resilience4j Spring-Boot-4 starter availability (else core modules + explicit config, new ADR).
- [ ] Testcontainers 2.x API vs the authored `*IT` bases.
- [x] Spring Cloud Gateway coordinates/version for Spring Boot 4 — Spring Cloud 2025.1.3, gateway
      builds green.
- [x] Kafka-clients 4.3.1 vs the Boot-4-managed version — pinned; convergence passes.
- [x] Admin UI npm build — verified green; wired under the `ui` Maven profile.

## Phase 2: loan core + ledger (Docker session / follow-ups)

- [ ] RUN in the Docker session: `scripts/e2e/phase2.sh` against the full stack (loan created from
      application.accepted, simulate-disbursement, 12-month accrual by business date, full repayment →
      CLOSED, foreclosure quote, ledger reversal rejected twice, trial balance zero throughout).
- [ ] loan-account/ledger Testcontainers ITs (deferred): the jqwik stateful ledger invariant at the
      SQL level; inserting an unbalanced journal entry directly in SQL fails at COMMIT (deferred
      constraint trigger); replay `loan.events`+`disbursement.events` from offset 0 and compare the
      trial balance and every account balance; 50 concurrent events for one loan — no lost updates,
      no duplicate postings; accrual-idempotency at the DB unique key.
- [ ] Postgres role hardening: in addition to the append-only triggers (enforced in the migrations),
      withhold UPDATE/DELETE grants on `journal_entries`/`postings`/`allocations`/`loan_state_history`/
      `accruals` from the service roles (postgres-init, needs a separate migrator/owner role so the
      service role is INSERT/SELECT only).
- [ ] Schema Registry BACKWARD compatibility for the Phase 2 events (loan.*, disbursement.*,
      repayment.received, ledger.*) via `scripts/check-schema-compat.sh`.
- [ ] Outbox relay + Kafka producer bean are still unwired across all services (Phase 0/1 deferral);
      wire the relay poller so loan/ledger events actually publish in the Docker session.
- [ ] Advance held in Customer suspense is recorded on `loan.repayment-allocated` but not yet
      auto-applied when the next instalment falls due (domain-rules "Repayment allocation" rule 3).
- [ ] Penal accrual job (`loan.penal-accrued`) and the Delinquent/WrittenOff transitions are modelled
      (PenalAccrual, DpdBucket, state machine) but the daily penal/DPD job and write-off command are
      Phase 4 work; wire them there.
- [ ] Tag `phase-2-done` once the Docker-session items above pass (mirrors the Phase 0/1 discipline).

## Phase 3 (money movement) — deferred and known gaps

- [ ] Docker session: run `scripts/e2e/phase3.sh` (happy path, unknown-outcome, duplicate/delayed/
      conflicting callbacks, three-rail-failures → CANCELLED, **crash test**, waterfall, duplicate
      payment callback, returned debit, foreclosure), with the `workflow` (Temporal) profile up.
- [ ] Docker session: Temporal dev-server run proving `setUsingVirtualThreads` /
      `setUsingVirtualWorkflowThreads` end-to-end (ADR 0008), plus the live crash/replay.
- [ ] Docker session: broker/Redis-backed callback dedup on `(railRef,status)` and
      `(providerReference,status)`; the k6 `scripts/k6/phase3-callback-latency.js` p95 < 200 ms check.
- [ ] Schema Registry BACKWARD check for the Phase 3 events (repayment.failed/reversed, mandate.*,
      loan.repayment-reversed/foreclosed) and the `repayment.received` field additions.
- [ ] Foreclosure clears outstanding principal + due interest/penal and waives future scheduled
      interest; the quote's actual/365 accrued interest and charge beyond those components are held as
      advance (ADR 0005 simplification). Recognise that residual as interest income + charge in a
      follow-up so the loan's suspense nets to zero at foreclosure.
- [ ] Foreclosure via payment link: thread `purpose`/`foreclosureQuoteId` from `POST /repayments`
      through to the payments callback (today only the manual-receipt path carries them; the
      payment-link callback records an ordinary repayment). A pending-repayment-by-linkId lookup.
- [ ] `GET /api/loans/due?date=` on loan-account for the repayment auto-debit job's
      `DueInstalmentsPort` (gateway-mediated); not built yet (auto-debit returns no due loans until).
- [ ] Repayment outbox relay + Kafka producer wiring (same deferral as the other services).
- [ ] Admin UI Phase 3 pages (disbursement monitor, repayments, mandates, manual-collection dialog,
      simulator rail/mandate/payments controls) — authored under `-Pui`; OIDC login verified in Docker.
- [ ] Tag `phase-3-done` once the Docker-session items above pass (mirrors the Phase 0/1/2 discipline).

## Phase 4 (collections and operations) — deferred and known gaps

- [ ] Docker session: run `scripts/e2e/phase4.sh` (3 loans over 12 months, daily jobs in order; A
      on-time never opens a case; B opens at DPD 1, runs DPD 1/3/7, then resolves; C → NPA →
      two-person write-off), with the `workflow` (Temporal) profile up for `collections`.
- [ ] Docker session: **dunning crash test** — kill `collections` mid-workflow, restart, deliver the
      next DPD tick, and confirm no dunning action is repeated (`(caseId, stepId)` guard).
- [ ] Docker session: Temporal dev-server run for the `collections` worker (second embedded Temporal
      service; virtual-thread options per ADR 0008/0011).
- [ ] Docker session: notification acceptance — replay sends no second message; a 22:00 message is
      QUEUED then released by the morning job; a channel with failureRate=1 retries and ends FAILED
      after `lms.notification.max-attempts`; consent-withheld → SUPPRESSED.
- [ ] Docker session: reconciliation — seed the simulator feeds to mirror the ledger's money
      movements; a clean day is all MATCHED; with `reconDriftRate`/drift on, each of
      MISSING_IN_LEDGER / MISSING_IN_BANK / AMOUNT_MISMATCH / DUPLICATE is detected exactly once.
- [ ] Schema Registry BACKWARD check for the Phase 4 events (loan.penal-accrued, loan.dpd-updated,
      loan.installment-due-soon, loan.written-off, collections.*, notification.*) and the additive
      `ledger.recon-completed` fields (reconType + counts, defaulted).
- [ ] Outbox→Kafka relay wiring for `collections` and `notification` (same deferral as the other
      services); the live inbox-idempotent consumption of the loan/repayment/collections events.
- [ ] Consent is permissive (`PermissiveConsentPolicy`); wire a consent cache fed by
      `customer.events` (or a gateway call) so a withheld channel yields SUPPRESSED for real.
- [ ] Notification backoff is a plain sleep; move channel retries behind Resilience4j (retry +
      circuit breaker + bulkhead) like the other outbound calls, with config-driven parameters.
- [ ] Reconciliation reconciles the money-movement entries by their external reference (ADR 0012
      simplification), not every account-1001 posting line; revisit if a future product needs the
      full 1001 line-level match or additional accounts.
- [ ] k6: a Phase 4 load check (e.g. daily-job throughput / dunning tick fan-out) under
      `scripts/k6/` — not yet authored.
- [ ] Admin UI Phase 4 pages (collections queue + case detail, write-off requests, notification log,
      reconciliation results) — authored and type-checked under `-Pui`; OIDC login + live data
      verified in Docker.
- [ ] Tag `phase-4-done` once the Docker-session items above pass (mirrors the Phase 0–3 discipline).

## Phase 5 (hardening) — deferred and known gaps

Docker-independent Phase 5 work is built and `./mvnw clean verify`-green (reporting service, audit
events, product-config versioning, key rotation, gateway routes/SSE/DLT, Admin UI pages, all
scripts and docs). These remain for the Docker/CI session (mirrors Phases 0–4):

- [ ] RUN perf (k6) `scripts/perf/*.js` and record baselines + before/after in `docs/perf/`; JFR
      profile the top bottlenecks (`jfr` compose profile); run the GC / compact-headers / AOT matrix
      and adopt the winner per service in the Jib `jvmFlags`, appending the choices to ADR 0015.
- [ ] RUN every `scripts/chaos/chaos.sh` scenario, each followed by `scripts/verify-invariants.sh`;
      fill in `docs/chaos-report.md`.
- [ ] RUN `scripts/soak/soak.sh` (1,000 loans) and the invariant checker — zero violations required.
- [ ] RUN `scripts/check-k8s-readiness.sh` (probes, non-root, env-only config, graceful SIGTERM).
- [ ] Prove `reporting` rebuild-from-offset-0 yields identical figures to the live read models
      (reset the `reporting` group offsets, truncate read tables, replay).
- [ ] Security acceptance: prove CI fails on a deliberately introduced high-severity dependency and
      on a planted secret (`.github/workflows/security.yml`), then remove both; audit a full e2e run's
      logs for PII leakage (expect none); run the key-rotation dry-run on a copy of the data.
- [ ] Schema Registry BACKWARD check for the new `audit.events` schema (`scripts/check-schema-compat.sh`).
- [ ] Wire the dashboard Redis "ping": services (at minimum `reporting` on snapshot capture) publish
      a lightweight change event on `lms:dashboard:events` so the gateway SSE pushes immediately; the
      Admin UI already falls back to polling. Add a `DashboardNotifier` port + Redis adapter.
- [ ] Expose the ledger trial-balance diff as a micrometer gauge `lms_ledger_trial_balance_diff` so
      the Grafana stat panel and alerting read it directly (today it is an event + the invariant script).
- [ ] Retrofit the `AuditRecorder` into the remaining admin endpoints (simulator control, DLT replay
      in the gateway — needs an outbox-bearing home, reconciliation resolution, manual collection
      actions). Product-config creation and write-off approve/reject are wired now (ADR 0014).
- [ ] Optional mTLS profile: generate the dev CA + per-service certs, add the `mtls` compose profile
      and the SSL env wiring (docs/threat-model.md); certificate issuance/rotation.
- [ ] Generate the full Bruno/Postman collection from `contracts/.../openapi/gateway.yaml` with an
      OpenAPI→Bruno tool; `docs/bruno/` holds a hand-authored collection of the key endpoints now.
- [ ] Tag `phase-5-done` and `v1.0.0` once the Docker/CI items above pass (mirrors the Phase 0–4
      discipline; `phase-3-done`/`phase-4-done` are created in the same session).

### Phase 5 security scanning — framework CVEs and gate design (follow-ups)

- [ ] Upgrade the Spring Boot BOM (and thereby tomcat-embed-core, jackson, netty, …) to clear the
      HIGH/CRITICAL framework CVEs the Trivy **image scan** reports. The image scan is currently
      report-only (informational) in `.github/workflows/security.yml`; the fs dependency scan and
      secret scan are the hard gates. Make the image scan fail-on-HIGH/CRITICAL once the framework
      CVEs are remediated (and base-image OS CVEs are addressed by rebasing).
- [ ] Revisit the two `.trivyignore` baselines when their upstreams move: guava (CVE-2026-102554,
      coupled to the Temporal/gRPC convergence pins, ADR 0008) and the optional `at.yawk.lz4:lz4-java`
      fork (CVE-2026-106451, not resolved onto any classpath).
- [ ] Run the planted-vulnerability and planted-secret acceptance demonstration once (prove the
      dependency scan and gitleaks fail, then remove the planted artifacts).
