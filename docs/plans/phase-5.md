# Phase 5 plan — Hardening

Goal (phase prompt): measured performance, proven failure behaviour, reporting read models, a
security pass and operator-grade documentation, **without adding new business features**.

## Pre-flight (step 2)

- `./mvnw clean verify` is **green** (checked 2026-10-26, exit 0).
- Tag `phase-4-done` is **withheld by design** — same discipline as Phases 1–4 (ADR 0001 / backlog):
  this machine has no Docker, so every container-dependent DoD item is authored but executed in one
  batched Docker session after all phases. Tags `phase-3-done`/`phase-4-done` are created then.
  Proceeding with the Docker-independent Phase 5 work does not change money handling or a locked
  decision, so no ask is required (CLAUDE.md working agreement).

## Scope boundary (done-now vs Docker-deferred)

**Done now (must be `./mvnw clean verify`-green, test-first for money/aggregation/state/crypto):**
- `audit.events` Avro contract; `lms-security` audit core (pure factory, tested) + outbox publisher.
- `reporting` service: read-model projections from every domain topic, denormalised tables,
  aggregation queries (portfolio snapshot, funnel, collection efficiency, DPD distribution, vintage),
  audit-log store, REST query API, ArchUnit, rebuild-from-offset-0 design.
- Product-configuration versioning in `loan-account` (create effective-dated version; loans keep
  their pinned version) — test-first on the versioning/state, no change to money *handling*.
- Key-rotation: versioned `KeyRing` cipher in `lms-security` (encrypt-current / decrypt-any /
  re-encrypt), test-first.
- Admin UI pages (dashboards, product config, audit log, DLT viewer) + api clients + gateway routes.
- Scripts authored: `scripts/verify-invariants.sh`, `scripts/soak/`, `scripts/chaos/`,
  `scripts/perf/` (k6), `scripts/check-k8s-readiness.sh`.
- Security docs: `docs/threat-model.md`, CI scanning workflow (dep/secret/image), mTLS profile doc,
  key-rotation procedure.
- Operator docs: `docs/runbook.md` failure sections, Grafana dashboards JSON, ADR index, arch
  diagram, Postman/Bruno collection, README, backlog, `docs/perf/`, `docs/chaos-report.md` template.

**Docker/runtime session (authored now, run later):** the actual k6 perf runs + p95 targets; JFR
profiling; GC/compact-header/AOT benchmarks (ADR 0015 records method + deferral); every chaos
scenario + `verify-invariants.sh`; the 1,000-loan soak; CI proving a planted vuln/secret fails;
no-PII assertion over live e2e logs; `reporting` rebuild-from-0 equality; k8s SIGTERM graceful-stop.
Tag `phase-5-done` / `v1.0.0` only after those pass (mirrors Phases 0–4).

## Tasks, in order (small vertical slices, each build-green + conventional commit)

1. **ADR 0013** reporting read-model + rebuild-from-offset-0; **ADR 0014** audit events;
   **ADR 0015** GC/compact-header/AOT benchmark method + deferral; **ADR 0016** key rotation.
   (Written alongside the slice that implements each.)
2. **Contracts:** `AuditEvent.avsc` on new topic `audit.events` (key = actor id). New schema,
   BACKWARD-safe. No change to any existing event.
3. **lms-security audit core:** `AuditAction`, `AuditEntry` (domain, pure), `AuditEventFactory`
   (RequestContext + before/after summary → envelope payload), `AuditPublisher` port + outbox-backed
   impl helper. Tests: factory maps actor/action/target/before/after/correlation; no PII in summary.
   Wire in two representative admin actions (product-config change, write-off approval); remaining
   per-service wiring → backlog (mechanism proven in-JVM).
4. **reporting service (new, 8110, ADR 0013):** pom, app, Kafka consumer config (group `reporting`,
   earliest), Flyway read-model + audit schema, projections (loan/application/disbursement/repayment/
   collections/ledger/audit), idempotent via inbox, pure aggregation functions (test-first), REST
   query API, ArchUnit. jaCoCo excludes adapters/config as elsewhere.
5. **product-configuration versioning (loan-account, test-first):** effective-dated `products`
   version create + list + current; loan keeps pinned `product_version`. Migration. Gateway route +
   role. Acceptance test: new version created, existing loan unchanged.
6. **gateway:** routes for reporting (8110), product-config (loan-account), audit (reporting), SSE
   dashboard stream (Redis pub/sub → SSE), DLT viewer (admin of each `.dlt` topic) + roles.
7. **admin-ui:** Dashboards (portfolio/funnel/DPD/collection-efficiency, SSE live), Product config,
   Audit log (filters), DLT viewer (replay). api clients, routes, nav. Type-check under `-Pui`.
8. **scripts:** `verify-invariants.sh` (trial balance 0; per-loan principal/interest/penal =
   schedule = sub-ledger; no stuck PendingDisbursal; outbox drained; no duplicate eventId; ≤1
   successful disbursement/loan; DLT empty/explained). `soak/soak.sh` (1,000 loans, mixed behaviour).
   `chaos/*` (kill/restart Kafka/Redis/Temporal/DB; pause; duplicate storm; flaky/down presets).
   `perf/*.js` (k6: app→decision, repayment callback, mixed gateway reads). `check-k8s-readiness.sh`.
9. **security:** `docs/threat-model.md` (STRIDE per boundary); `.github/workflows/security.yml`
   (OWASP dependency-check, gitleaks, Trivy image scan); mTLS profile doc; key-rotation code (ADR
   0016) + `docs/runbook.md` rotation procedure + test on a data copy.
10. **operator docs:** runbook failure sections, Grafana dashboards JSON (service health, Kafka lag,
    outbox lag, DLT counts, Temporal failures, trial-balance metric), ADR index, arch diagram
    (updated for reporting + audit.events), Postman/Bruno collection from OpenAPI, README.

## Risks / open questions

- **Where audit is published.** `lms-security` is a platform lib with no DB/outbox, and the do-not
  list forbids Kafka except through the outbox. Decision (ADR 0014): `lms-security` owns the audit
  *domain + factory + port*; each service publishes through **its own** outbox. Prove the mechanism
  in-JVM in two admin actions now; retrofit the rest in the Docker session (backlog). No rule weakened.
- **`reporting` must not read other services' DBs.** It is event-sourced from topics only (ArchUnit:
  no cross-service JDBC; it owns only its read-model DB). Rebuild = replay from offset 0.
- **Product config is money-adjacent.** It is versioned *configuration*, already modelled in Phase 2;
  Phase 5 only adds create-new-version + effective dating. No change to `Money`, rounding, schedule
  or allocation. Loans pin their version (domain-rules "one product"). If anything forced a change to
  money handling I would stop and ask — it does not.
- **Perf/GC/AOT numbers require a running stack + container images.** Measured in the Docker session;
  ADR 0015 fixes the method and the "measure before optimise" rule so no premature tuning ships.
- **No new business feature.** Reporting is read-only projection; audit is cross-cutting; product
  config versions an existing entity. New ideas → `docs/backlog.md`.

## Progress log

Done now, each `./mvnw clean verify`-green and committed as a slice:
- Contracts: `audit.events` (`AuditEvent.avsc`). ADRs 0013–0016, ADR index.
- `lms-security`: audit core (`AuditEntry`/`AuditOutcome`/`AuditSummary`/`AuditRecorder`, 10 tests);
  `KeyRing` versioned key rotation (6 tests).
- `reporting` service (8110): projections from every domain topic + audit, pure aggregations
  (portfolio/funnel/collection-efficiency/vintage/DPD) in basis points, REST query API, daily
  snapshot job, ArchUnit; 35 tests, coverage met.
- `loan-account`: DB-backed effective-dated product versioning (`ProductRepository`,
  `DbProductCatalog`, `ProductAdminService`, `ProductController`); audit wired into product-create
  and write-off approve/reject; `ProductAdminServiceTest`.
- `gateway`: reporting/audit/product routes, dashboard SSE fan-out, DLT viewer/replay, roles.
- `admin-ui`: Dashboards, Product config, Audit log, DLT viewer (type-check + vite build green).
- Scripts: `verify-invariants.sh`, `soak/soak.sh`, `chaos/chaos.sh`, `perf/*.js`,
  `check-k8s-readiness.sh`; compose + postgres-init wired for reporting.
- Security/docs: CI `security.yml` (gitleaks + Trivy) + `.gitleaks.toml`; `threat-model.md`;
  runbook failure playbook + key-rotation procedure; Grafana ops dashboard + prometheus targets;
  architecture diagram; `perf/README.md`; `chaos-report.md`; Bruno collection; README; backlog.

Deferred to the Docker/CI session (see `docs/backlog.md` "Phase 5"): the perf/chaos/soak/k8s runs,
GC/AOT matrix, CI scan acceptance proof, reporting rebuild-from-0, mTLS certs, the dashboard Redis
ping, remaining audit wiring, and the `phase-5-done`/`v1.0.0` tags.

## Definition of done (now)

`./mvnw clean verify` green incl. Spotless, enforcer, ArchUnit, coverage gates; test-first for the
reporting aggregations, audit factory, product-version state and key-rotation crypto; ADRs
0013–0016; all Docker-session scripts/docs authored and listed in README/runbook/backlog;
`phase-5-done`/`v1.0.0` withheld until the Docker-session items pass.
