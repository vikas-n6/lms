# Micro-Loan LMS

A micro-loan Lending Management System built as Java 25 microservices: eligibility, origination,
loan accounts, disbursement, repayment, collections, a double-entry ledger, and an Admin UI. A
personal learning project — regulations are mocked and every external party (bureau, KYC, payment
rail, SMS) is a simulator we own. See `CLAUDE.md`, `docs/architecture.md` and `docs/domain-rules.md`.

## Status — Phase 0 (Foundations)

Green multi-module Maven build on JDK 25, shared platform libraries, the Avro event contracts, a
reference service (`customer`) that publishes a reliable event through the transactional outbox, a
gateway, and the full local-infra definitions.

> **Docker note:** the machine used to build Phase 0 has no container runtime, so the
> container-dependent items (Testcontainers `*IT`, the ADR 0001 broker/Temporal smoke tests,
> `docker compose up`, the e2e run, Admin UI OIDC login) are **authored but not yet executed**.
> They are tracked in `docs/backlog.md` and run in a session with Docker. The `phase-0-done` tag is
> withheld until they pass.

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
| Notification stub | http://localhost:8109 |
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
```

## Layout

```
platform/   lms-money lms-concurrency lms-config lms-events lms-security lms-observability lms-outbox lms-testkit
contracts/  Avro schemas + OpenAPI (generated jar; services depend on it, never on each other)
services/   customer gateway  (origination, decision, loan-account, ... added in later phases)
deploy/     docker-compose + profiles, postgres-init, kafka-init, keycloak realm, observability
scripts/    new-service.sh, check-schema-compat.sh, e2e/
docs/       architecture, domain-rules, adr/, plans/, runbook, backlog
```

See `docs/runbook.md` for operating and troubleshooting.
