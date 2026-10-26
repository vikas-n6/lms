# Micro-Loan LMS

A micro-loan Lending Management System built as Java 25 microservices: eligibility, origination,
loan accounts, disbursement, repayment, collections, a double-entry ledger, and an Admin UI. A
personal learning project — regulations are mocked and every external party (bureau, KYC, payment
rail, SMS) is a simulator we own. See `CLAUDE.md`, `docs/architecture.md` and `docs/domain-rules.md`.

## Status — Phase 4 (Collections and operations)

Phases 0–3 (foundations, origination, decisioning, loan core + ledger, money movement) plus Phase 4:
delinquent loans move through DPD buckets, penal charges accrue, dunning runs as a durable workflow,
notifications go out reliably, and a daily reconciliation compares the ledger with the simulated
bank and rail reports.

- **`loan-account` daily jobs** run in a fixed order per business date, each idempotent per
  `(loanId, businessDate)`: mark DUE/OVERDUE (derived) → interest accrual → penal accrual (36% APR
  simple on overdue principal + interest, only after the grace days) → DPD. It moves loans between
  `Active` and `Delinquent`, flags `NPA` at DPD 91+, publishes `loan.penal-accrued` and
  `loan.dpd-updated` (every day DPD > 0, plus once on return to zero), and a pre-due job emits
  `loan.installment-due-soon`.
- **Two-person write-off**: an admin raises a request (reason required, DPD ≥ 180); a *different*
  admin approves it; on approval the loan is written off, receivables go to zero and
  `loan.written-off` drives the ledger's bad-debt posting.
- **`collections` (8107)** opens a case at DPD 1+ and runs a durable Temporal `DunningWorkflow`
  (`dun-{loanId}`) that advances on DPD *ticks* (never wall-clock timers — ADR 0011), fires each
  configured plan step once (`(caseId, stepId)` idempotency), pauses on a promise-to-pay and resumes
  when broken, and cancels remaining steps when payment clears the overdue.
- **`notification` (8109)** renders versioned templates over SMS/email/push/WhatsApp simulators,
  dedups on `(eventId, templateId, channel)`, retries with backoff, respects consent and a
  quiet-hours window (held overnight, released in the morning).
- **Reconciliation** in `ledger` matches the bank statement and rail settlement report against the
  money-movement entries, classifying MATCHED / MISSING_IN_LEDGER / MISSING_IN_BANK /
  AMOUNT_MISMATCH / DUPLICATE and publishing `ledger.recon-completed` (ADR 0012).

The money- and state-critical core — penal accrual and DPD derivation, the write-off state
transition, the penal/write-off ledger templates, the dunning workflow (proven in-JVM via Temporal's
`TestWorkflowEnvironment` + `WorkflowReplayer`), notification dedup/quiet-hours/retry, and the
reconciliation matcher — is proven under `./mvnw clean verify`.

> **Docker note:** the build machine has no container runtime, so container-dependent items
> (Testcontainers `*IT`, the live Temporal/Kafka/Redis runs, the dunning crash/restart test,
> `docker compose up`, the `scripts/e2e/phase*.sh` runs incl. `phase4.sh`, the k6 checks, the
> outbox→Kafka relay, live channel delivery, Admin UI OIDC login) are **authored but not yet
> executed**. They are tracked in `docs/backlog.md` and run in a session with Docker. The
> `phase-3-done` and `phase-4-done` tags are withheld until they pass (the same discipline as the
> earlier phases).

## Prerequisites

- **JDK 25** (`java -version` → 25). The Maven Wrapper must run on 25:
  `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` on macOS.
- **Docker Compose v2** — for the local stack and integration tests (install Docker Desktop or
  Colima if missing).
- **Node 22+ / npm** — for the Admin UI.

## Build and test

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS; or any JDK 25 home
./mvnw clean verify                                # Spotless + enforcer + ArchUnit + coverage + tests
# Fast loop for one module:
./mvnw -T 1C -pl services/customer -am verify
```

The build pins every framework version (resolved from Maven Central) in the parent POM; see
`docs/adr/0001-versions.md`. Service POMs contain no version numbers.

## Run the local stack

Uses the `Makefile` wrapper over `deploy/docker-compose.yml` (copy `deploy/.env.example` →
`deploy/.env` first):

```bash
make up-core          # Postgres, Kafka (KRaft), Schema Registry, Redis, Kafka UI
make up-auth          # Keycloak with the imported lms realm
make up-full          # everything (adds Temporal, observability, services, UI)
make images           # build service container images with Jib
make down             # stop
make reset            # stop + wipe volumes
```

| Thing | URL |
| --- | --- |
| Gateway | http://localhost:8080 |
| Customer service | http://localhost:8101 |
| Origination service | http://localhost:8102 |
| Decision service | http://localhost:8103 |
| Collections service | http://localhost:8107 |
| Notification service | http://localhost:8109 |
| Simulators | http://localhost:8190 |
| Admin UI | http://localhost:3002 (image) / 5173 (dev) |
| Kafka UI | http://localhost:8089 |
| Keycloak | http://localhost:8180 (admin/admin) |
| Temporal UI | http://localhost:8233 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

Realm users: `admin`/`admin`, `analyst`/`analyst`, `customer1`/`customer1`.

## End-to-end check

```bash
make e2e              # scripts/e2e/phase0.sh: token -> POST via gateway -> event on Kafka ->
                      # outbox drained -> survives a Kafka restart
scripts/e2e/phase1.sh # Phase 1: register -> KYC -> bank -> apply -> decision -> offer -> accept,
                      # plus persona reject/refer-override/delinquent, velocity and bureau-down
scripts/e2e/phase3.sh # Phase 3: disbursement saga (happy/unknown-outcome/duplicate/conflict/crash),
                      #          repayment waterfall, returned debit, foreclosure; trial balance zero
scripts/e2e/phase2.sh # Phase 2: accepted application -> loan -> simulate-disbursement -> accrue over
                      # the life -> full repayment -> CLOSED; ledger trial balance stays zero
scripts/e2e/phase4.sh # Phase 4: 3 loans over 12 months running the daily jobs in order; A on-time,
                      #          B recovers, C -> NPA -> two-person write-off; dunning, notifications
                      #          (dedup/quiet-hours/retry) and bank/rail reconciliation; TB zero
```

## Layout

```
platform/   lms-money lms-concurrency lms-config lms-events lms-security lms-observability lms-outbox lms-testkit
contracts/  Avro schemas + OpenAPI (generated jar; services depend on it, never on each other)
services/   customer gateway origination decision loan-account ledger notification (disbursement, repayment, collections, reporting in later phases)
deploy/     docker-compose + profiles, postgres-init, kafka-init, keycloak realm, observability
scripts/    new-service.sh, check-schema-compat.sh, e2e/
docs/       architecture, domain-rules, adr/, plans/, runbook, backlog
```

See `docs/runbook.md` for operating and troubleshooting.
