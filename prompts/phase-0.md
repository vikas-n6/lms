# Phase 0 prompt (paste this whole file into Claude Code)

You are working in the `lms` repository on Phase 0.

1. Read CLAUDE.md, docs/architecture.md, docs/domain-rules.md and every ADR in docs/adr/.
2. This is the first phase. Confirm the repository is empty apart from CLAUDE.md, docs/ and prompts/, and that Java 25 is installed.
3. Write your plan to docs/plans/phase-0.md (tasks in order, risks, open questions),
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

## Phase 0: Foundations

**Goal:** a green multi-module Maven build, shared platform libraries, a complete local infrastructure stack, one reference service that publishes a reliable event, a gateway with JWT validation, and an Admin UI shell with login.

**Read first:** `CLAUDE.md`, `docs/architecture.md`, `docs/domain-rules.md` (Money type and Event contract sections). Write your plan to `docs/plans/phase-0.md`, then proceed.

### Tasks

1. **Toolchain check.** Confirm `java -version` reports 25 and Docker Compose v2 is available. If JDK 25 is missing, install it (SDKMAN or Temurin) and note how in the README.
2. **Version resolution and ADR 0001.** Look up the latest GA of Spring Boot 4.x, Spring Kafka, Temporal Java SDK, Avro, Confluent Schema Registry client, jOOQ, Redisson, Resilience4j, Testcontainers, jqwik, ArchUnit. Prove they run on Java 25 with a throwaway smoke test per library. The Temporal SDK is the least verified (no explicit Java 25 statement found): run a workflow and an activity on JDK 25 with both virtual-thread options enabled (`WorkerOptions.setUsingVirtualThreads`, `WorkerFactoryOptions.setUsingVirtualWorkflowThreads`). Record the chosen versions and any incompatibility in `docs/adr/0001-versions.md`. Do not guess versions from memory.
3. **Maven skeleton.** Parent `pom.xml`, `lms-bom`, Maven Wrapper, enforcer, Spotless, JaCoCo, Surefire and Failsafe with the preview flag in `argLine`, `.editorconfig`, `.gitignore`, `.github/workflows/ci.yml` (Temurin 25, `./mvnw clean verify`, Testcontainers enabled).
4. **Platform modules**, each with tests:
    - `lms-money`: `Money`, `Rate`, `allocate`, rounding rules exactly as in `docs/domain-rules.md`, currency-aware minor-unit scale (ISO 4217 fraction digits), with jqwik property tests. 90% coverage gate.
    - `lms-events`: `EventEnvelope` record, `EventPublisher` port, Avro serde with Schema Registry, header propagation for trace and correlation ids.
    - `lms-outbox`: outbox and inbox table migrations (as a Flyway locations folder that services include), `OutboxRepository` (jOOQ or JDBC), a virtual-thread relay using `FOR UPDATE SKIP LOCKED`, and an `IdempotentConsumer` helper that writes `inbox_processed` in the same transaction as the handler. Include retry-topic and DLT routing.
    - `lms-security`: JWT resource-server configuration, `RequestContext` carried by `ScopedValue` and populated by a servlet filter, an `Idempotency-Key` filter (Redis plus Postgres fallback table), PII masking helpers.
    - `lms-observability`: OpenTelemetry setup, JSON logging with `traceId` and `loanId` from `RequestContext`, standard metric names.
    - `lms-concurrency`: `Fanout.callAll(...)` wrapper over `StructuredTaskScope` (preview) with timeout and cancellation, and a `Lazy<T>` wrapper over Stable Values (preview). These are the only classes allowed to touch preview APIs.
    - `lms-config`: typed configuration helpers, the `Clock` bean, and the default-currency property `lms.currency.default` (INR).
    - `lms-testkit`: Testcontainers base classes (`PostgresIT`, `KafkaIT`, `RedisIT`), a controllable `Clock`, and the shared ArchUnit rules from `docs/architecture.md`.
5. **Contracts.** `contracts/` module with the Avro envelope and common types, `avro-maven-plugin`, a script `scripts/check-schema-compat.sh` that checks compatibility against a running Schema Registry, and OpenAPI skeletons for the gateway.
6. **Local infrastructure.** `deploy/docker-compose.yml` with the profiles from `docs/architecture.md`. Include: Postgres with per-service databases and roles from `postgres-init/`, Kafka (KRaft) plus a `kafka-init` job that creates every topic in the catalogue with retry and DLT variants, Schema Registry, Redis, Kafka UI, Keycloak with an imported `lms` realm (users `admin`/`analyst`/`customer1`, a client per service, a public client for `admin-ui`), Temporal dev server and UI, and the observability stack with Grafana datasources pre-provisioned. Provide `.env.example` and a `make` or `scripts/` wrapper (`up core`, `up full`, `down`, `reset`).
7. **Reference service: `customer`.** Full hexagonal skeleton with Flyway, jOOQ or Spring Data JDBC, REST (`POST /customers`, `GET /customers/{id}`), the outbox publishing `customer.registered`, health probes, Dockerfile (Jib), and ArchUnit tests. Phase 1 will extend it, and every later service copies its structure.
8. **Service scaffolding script.** `scripts/new-service.sh <name> <port>` copies the reference structure, renames packages, registers the module and adds the compose entry.
9. **Gateway.** Spring Cloud Gateway on virtual threads, JWT validation against Keycloak, route `/api/customers/**` to `customer`, Redis-backed rate limiting, and an aggregated `/api/status` endpoint that returns each service's health.
10. **Admin UI shell.** `admin-ui/` with Vite, React, TypeScript, TanStack Query, Tailwind and shadcn/ui. OIDC login against Keycloak (Authorization Code with PKCE), a protected-route wrapper, an app shell (sidebar, header with user and logout), and a Status page showing `/api/status`. Format any amount with `Intl.NumberFormat` using the currency code from the API. Wire it into Maven with `frontend-maven-plugin` so `./mvnw verify` builds it, and add an nginx-based image.
11. **End-to-end script** `scripts/e2e/phase0.sh`: bring up `core`, `auth` and `apps`, obtain a token, `POST /api/customers` through the gateway, then assert that the `customer.registered` event arrives on Kafka and that the outbox row is marked published. It must also kill the Kafka container mid-run, restart it, and show the event still gets delivered once.
12. **Docs.** `README.md` quickstart, `docs/runbook.md` (start, stop, reset, where to look when things fail), and one ADR for any deviation.

### Acceptance tests

- `lms-money` property tests pass for at least 10,000 generated cases each, including currencies with 0 and 3 minor-unit digits (JPY and KWD) to prove the scale is not hard-coded.
- ArchUnit fails the build if a class uses `ThreadLocal`, if `domain` imports Spring, or if anything outside `lms-concurrency` imports `StructuredTaskScope`. Prove it by adding a deliberately bad class in a test and watching the rule fail.
- Outbox integration test: stopping Kafka mid-publish, restarting, and checking exactly one delivery with the same `eventId`.
- Idempotency test: two identical `POST /customers` calls with one `Idempotency-Key` create one customer and return the same response.
- Gateway rejects a request without a valid token (401) and with the wrong role (403).
- `./mvnw clean verify` from a clean checkout passes in CI.

### Do not

- Do not add any business logic beyond the customer skeleton.
- Do not use JPA/Hibernate, Lombok, or `ThreadLocal`.
- Do not use preview APIs outside `lms-concurrency`.
- Do not hard-code secrets, ports or hostnames in source code; use environment variables with local defaults.

### Exit checklist

- [ ] `./mvnw clean verify` green locally and in CI
- [ ] `scripts/e2e/phase0.sh` passes, including the Kafka restart scenario
- [ ] `docker compose --profile full up` brings every container to healthy
- [ ] Admin UI login works and the Status page shows all services
- [ ] ADR 0001 records versions and Java 25 compatibility findings, including the Temporal smoke test
- [ ] Tag `phase-0-done`
