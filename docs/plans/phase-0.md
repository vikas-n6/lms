# Phase 0 plan — Foundations

Status date: 2026-10-11

## Environment findings (gate check)

- **Java 25**: present — `25.0.4.1` LTS at `/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`. `JAVA_HOME` is unset in the shell; the Maven Wrapper is run with `JAVA_HOME` pointed at JDK 25 (system Maven otherwise runs on Homebrew JDK 23).
- **Maven**: 3.9.9 (on JDK 23). We commit and use the Maven Wrapper (`./mvnw`) instead.
- **Node/npm**: Node 26.4.0 / npm 11.17.0 — fine for `admin-ui`.
- **Docker / Compose v2**: **NOT installed** (no `docker`/`podman`/`colima`/socket). Decision (with the user): do **all Docker-independent work now**, and **defer** the container-dependent items to a later session once Docker is installed. The deferred set is tracked in `docs/backlog.md` and ADR 0001.

### Deferred until Docker is available (cannot run now, will not be faked)

- ADR 0001 **runnable** smoke tests (Kafka broker, Schema Registry, Temporal workflow+activity on JDK 25). Versions are resolved and pinned now; execution is deferred.
- All Testcontainers `*IT` tests (Failsafe): `lms-testkit` bases, outbox Kafka-restart IT, idempotency IT.
- `docker compose --profile full up`, Keycloak login, Status page end-to-end, `scripts/e2e/phase0.sh` execution.
- `git tag phase-0-done` is withheld until the deferred items pass (the phase is not "done" by its own Definition of Done until then).

All compose / Keycloak / postgres-init / e2e **files are authored** now so the later Docker session is purely "run and verify".

## Resolved versions (from authoritative `maven-metadata.xml`, 2026-10-11)

Spring Boot 4.1.1 · Spring Framework 7 (via Boot BOM) · Temporal SDK 1.40.0 · Avro 1.12.2 · Confluent SR client + avro serde 8.3.2 · Kafka clients 4.3.1 · jOOQ 3.21.9 · Redisson 4.8.0 · Resilience4j 2.4.0 · Testcontainers 2.0.5 · jqwik 1.10.2 · ArchUnit 1.5.1. Recorded in `docs/adr/0001-versions.md`.

Open risks to prove in the Docker session: Resilience4j Spring-Boot-4 starter, Testcontainers 2.x API, Spring Cloud Gateway coordinates for Boot 4, Kafka-clients 4.x vs the Boot-managed version.

## Tasks in order (small vertical slices; fast loop + commit after each)

1. **Maven skeleton**: parent `pom.xml`, `lms-bom`, Maven Wrapper, enforcer (requireJavaVersion `[25,)`, dependencyConvergence, banned deps), Spotless (Google Java Format AOSP), JaCoCo (90% on `lms-money`, 70% elsewhere), Surefire (`*Test`) + Failsafe (`*IT`) with `--enable-preview` in `argLine`, `.editorconfig`, `.gitignore`, `.github/workflows/ci.yml` (Temurin 25). → commit.
2. **`lms-money`** (test-first): `Money`, `Rate`, `allocate` (largest-remainder), rounding `HALF_UP`, ISO-4217-driven scale, overflow-checked arithmetic. jqwik property tests ≥10k cases incl. JPY (0) and KWD (3). 90% gate. → commit.
3. **`lms-concurrency`**: `Fanout.callAll(...)` over `StructuredTaskScope` (preview) + `Lazy<T>` over Stable Values (preview). Only module with `--enable-preview`. Tests. → commit.
4. **`lms-config`**: typed config helpers, `Clock` bean, `lms.currency.default=INR`. Tests. → commit.
5. **`lms-events`**: `EventEnvelope` record, `EventPublisher` port, header-propagation helpers. (Avro serde wired via `contracts`.) Tests. → commit.
6. **`lms-testkit`**: shared ArchUnit rules (ThreadLocal ban, domain-no-Spring, StructuredTaskScope confinement, BigDecimal confined to lms-money) with a deliberately-bad fixture proving each rule fails; controllable `Clock`; Testcontainers bases (`PostgresIT`/`KafkaIT`/`RedisIT`, compile now / run later). → commit.
7. **`lms-security`**: JWT resource-server config, `RequestContext` as `ScopedValue` + populating servlet filter, `Idempotency-Key` filter (Redis + Postgres fallback), PII masking helpers. Unit tests for masking + scoped value. → commit.
8. **`lms-observability`**: OTel setup, JSON logging with `traceId`/`loanId`, standard metric names. → commit.
9. **`lms-outbox`**: outbox/inbox Flyway location folder, `OutboxRepository`, virtual-thread relay (`FOR UPDATE SKIP LOCKED`), `IdempotentConsumer`, retry/DLT routing. Unit tests now; Kafka-restart IT authored (deferred run). → commit.
10. **`contracts`**: Avro envelope + common types, `avro-maven-plugin` codegen, OpenAPI gateway skeleton, `scripts/check-schema-compat.sh`. → commit.
11. **`customer` service**: hexagonal skeleton, Flyway, Spring Data JDBC, REST `POST`/`GET /customers`, outbox publishing `customer.registered`, health probes, Jib config, ArchUnit tests. → commit.
12. **`scripts/new-service.sh`**. → commit.
13. **`gateway`**: Spring Cloud Gateway on virtual threads, JWT validation, route `/api/customers/**`, Redis rate limit, `/api/status` aggregation. → commit.
14. **`admin-ui`**: Vite+React+TS, TanStack Query, Tailwind, shadcn/ui, Keycloak OIDC (PKCE), protected routes, app shell, Status page, `Intl.NumberFormat` currency formatting, `frontend-maven-plugin`, nginx image. → commit.
15. **compose + infra files** (authored, run deferred): `deploy/docker-compose.yml` w/ profiles, postgres-init per-service DB/role, kafka-init topic creation, Keycloak `lms` realm, observability provisioning, `.env.example`, `scripts/` wrappers. → commit.
16. **`scripts/e2e/phase0.sh`** (authored, run deferred). → commit.
17. **Docs**: `README.md`, `docs/runbook.md`, `docs/backlog.md`, ADRs. → commit.
18. **Final**: `JAVA_HOME=<jdk25> ./mvnw clean verify` (Docker-free profile), print exit checklist with pass/fail/deferred per item. Tag withheld pending Docker.

## Risks & open questions

- Resilience4j 2.4.0 may ship only a Spring-Boot-3 starter; if so use core modules + explicit config (ADR).
- Testcontainers 2.x API changes vs 1.x — ITs are authored against 2.x but unverified until Docker.
- Spring Cloud Gateway coordinates/version for Boot 4 to be confirmed at the gateway slice.
- Kafka-clients 4.3.1 vs Boot-4-managed version — may need an explicit override (dependencyConvergence).
- jqwik on JUnit 5 under Boot 4's managed JUnit — confirm platform version alignment.
- No money-handling or locked-decision ambiguities found; proceeding without further approval per the working agreement.
