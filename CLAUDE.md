# Micro-Loan LMS: CLAUDE.md

Standing instructions for every Claude Code session in this repository. Before starting any phase, also read `docs/architecture.md` and `docs/domain-rules.md`, and every ADR in `docs/adr/`. Phase prompts live in `prompts/`.

## Mission

Build a micro-loan Lending Management System (LMS) as Java 25 microservices, covering eligibility, origination, loan accounts, disbursement, repayment, collections and a double-entry ledger, plus an Admin UI. It is a personal learning project: regulations are mocked, and every external party (credit bureau, KYC, payment rail, SMS) is a simulator we own.

## Locked decisions (do not revisit without an ADR)

| Area | Decision |
| --- | --- |
| Market and compliance | Mocked behind a `CompliancePolicy` port with a permissive implementation |
| Currency | Configurable. The default comes from `lms.currency.default` (INR). Amounts are stored as `long` minor units inside a `Money` record, and the minor-unit scale comes from the currency, never hard-coded |
| Build tool | Maven (multi-module, Maven Wrapper committed) |
| Language | Java 25 (LTS), `<release>25</release>` |
| Services to services | Kafka events with the transactional outbox. Synchronous REST only through the gateway, or for simulator calls |
| Long-running workflows | Temporal, embedded inside Disbursement and Collections only. No global orchestrator |
| Product scope | One product: fixed-tenor, reducing-balance EMI personal micro-loan, behind `Product` and `ScheduleEngine` interfaces |
| Frontend | `admin-ui/`: React, TypeScript, Vite, TanStack Query, shadcn/ui, Tailwind, Keycloak OIDC |
| Deployment | docker-compose only, with profiles. Every service must still be Kubernetes-ready |
| Third parties | All mocked in the `simulators` module with configurable latency, failure, duplicate and delayed callbacks |

## Tech stack

| Layer | Choice |
| --- | --- |
| Framework | Spring Boot 4.x and Spring Framework 7. Resolve the latest GA from Maven Central at the start of Phase 0; never guess a version |
| Messaging | Apache Kafka 4.x (KRaft), Spring Kafka, Avro with Confluent-compatible Schema Registry |
| Cache and coordination | Redis 8 via Lettuce (plain use) and Redisson (locks) |
| Database | PostgreSQL 17+, one container, one database per service, Flyway migrations, jOOQ for the ledger and money paths, Spring Data JDBC elsewhere. No JPA/Hibernate |
| Workflow | Temporal (dev server in compose) with the Temporal Java SDK |
| Security | Keycloak (OIDC), JWT resource servers, service-to-service auth via client credentials |
| Resilience | Resilience4j (circuit breaker, retry, bulkhead, time limiter) on every outbound call |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki, Tempo. Structured JSON logs |
| Testing | JUnit 5, AssertJ, Testcontainers, jqwik (property tests), ArchUnit, Pact, k6 |
| Packaging | Jib or `spring-boot:build-image`, non-root images |

If any Spring Boot 4, Temporal SDK or Kafka client version is incompatible with Java 25, stop, write the finding into `docs/adr/`, and pick the closest working combination. Do not silently downgrade the Java release.

### Verified compatibility (checked 11 October 2026)

| Component | What was verified | Still to prove in Phase 0 |
| --- | --- | --- |
| Spring Boot | The [4.0 system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html) say it needs Java 17 or later, is compatible up to and including Java 25, and requires Spring Framework 7.0.x. [Spring Boot 4.1.0 was released on 10 June 2026](https://spring.io/blog/2026/06/10/spring-boot-4/). | Pin the latest 4.1.x. Smoke-test the Maven build and virtual threads on Java 25. |
| Kafka | The [Apache Kafka Java version page](https://kafka.apache.org/43/operations/java-version/) says Java 17, 21 and 25 are fully supported and recommends Java 25. [Confluent states](https://docs.confluent.io/cloud/current/client-apps/client-faq.html) the latest Java client supports JDK 25. | Run the broker image and the client under JDK 25 in Testcontainers. |
| Temporal Java SDK | [Release 1.37.0 (13 July 2026)](https://github.com/temporalio/sdk-java/releases/tag/v1.37.0) makes virtual-thread support stable, on JVM 21 or higher. No statement naming Java 25 explicitly was found. | Least verified item. Run a workflow and an activity on JDK 25 with both virtual-thread options enabled. |
| Not checked | Avro, Schema Registry client, jOOQ, Redisson, Resilience4j, Testcontainers, jqwik, ArchUnit. Tools that read bytecode (ArchUnit, mocking libraries) must understand Java 25 class files, so check each. | One smoke test per library, recorded in ADR 0001. |

## Java 25 usage rules

Use these features deliberately, not decoratively.

| Feature | Status in 25 | Rule |
| --- | --- | --- |
| Virtual threads | Final | Enable for Tomcat and Kafka listener executors. Never pool them. Use `Semaphore` or Resilience4j bulkheads for concurrency limits |
| Scoped Values | Final | `RequestContext` (traceId, userId, tenantId, idempotencyKey) travels as a `ScopedValue`. `ThreadLocal` is banned (ArchUnit rule) |
| Records, sealed interfaces, pattern matching for `switch`, record patterns | Final | All domain events, commands, `Money`, ids and state types. Switches over sealed types must be exhaustive with no `default` |
| Flexible constructor bodies | Final | Validate arguments before `super(...)` or `this(...)` in value objects |
| Module import declarations | Final | Allowed in tests and scripts only. Production code uses explicit imports |
| Compact object headers | Product flag | Turn on in container images with `-XX:+UseCompactObjectHeaders`, after a before/after benchmark recorded in an ADR |
| Generational Shenandoah or ZGC | Product | Benchmark both in Phase 5. Default to G1 until then |
| AOT cache (JEP 514, 515) | Final | Add AOT training and cache creation to the image build in Phase 5 |
| JFR method timing and tracing | Final | Provide a `jfr` compose profile and a documented recording recipe |
| Structured Concurrency | **Preview** | Only in the `lms-concurrency` platform module, for parallel fan-out. Other modules call its wrapper |
| Stable Values | **Preview** | Only in `lms-concurrency` or `lms-config`, for lazily initialised, once-only values |

Preview flag handling: `--enable-preview` must be set in compiler args, Surefire `argLine`, Failsafe `argLine`, and the container `JAVA_TOOL_OPTIONS`, and nowhere else. Check the JDK 25 release notes before using any API; if a preview API has changed shape, adapt the wrapper rather than leaking the change.

## Maven conventions

- Root `pom.xml` is the parent: `dependencyManagement` imports `spring-boot-dependencies`, Testcontainers BOM, Temporal BOM and the internal `lms-bom`. Service POMs contain no version numbers.
- `maven-enforcer-plugin`: `requireJavaVersion [25,)`, `dependencyConvergence`, `banDuplicatePomDependencyVersions`, banned dependencies (`commons-logging`, `log4j:log4j`, `org.hibernate*`, `javax.*` APIs).
- Spotless (Google Java Format, AOSP style) runs in `verify`. JaCoCo gate: 90% line coverage on `lms-money` and the schedule engine, 70% elsewhere.
- Surefire runs `*Test`. Failsafe runs `*IT` (Testcontainers) in `verify`.
- Fast loop: `./mvnw -T 1C -pl services/loan-account -am verify`. Full gate: `./mvnw clean verify`.
- Avro and OpenAPI generation lives in `contracts/`. Services depend on the generated jar, never on each other.

## Coding standards

1. **Hexagonal layout per service:** `domain` (pure Java, no Spring), `application` (use cases, ports), `adapters.in` (REST, Kafka listeners), `adapters.out` (JDBC, Kafka, Redis, HTTP, Temporal). ArchUnit tests enforce the direction of dependencies.
2. **Money:** never `double` or `float`. Use `Money(long minorUnits, Currency currency)`. The currency comes from configuration, never a literal. Arithmetic and rounding live only in `lms-money`.
3. **Time:** inject `java.time.Clock` everywhere. Never call `Instant.now()` in business code. Store UTC, display IST in the UI. Business dates (due dates, accrual days) use `LocalDate` in `Asia/Kolkata`.
4. **IDs:** UUIDv7 for aggregate ids. Human-facing loan numbers come from a separate sequence.
5. **Errors:** domain failures are sealed result types or typed exceptions mapped to RFC 9457 problem details. No `catch (Exception)` swallowing.
6. **Persistence:** every table has `created_at`, `updated_at`, `version` (optimistic locking). Money columns are `bigint` minor units with a `currency` column.
7. **Logging:** never log PII (phone, PAN-like ids, account numbers). Use masking helpers from `lms-security`. Every log line carries `traceId` and, where relevant, `loanId`.
8. **Config:** Twelve-factor. All configuration from environment variables with safe local defaults. No secrets in Git.
9. **Kubernetes-ready:** stateless, `/actuator/health/liveness` and `/readiness`, graceful shutdown, non-root image, resource-bounded.
10. **Comments** explain why, not what. Every non-obvious financial rule cites `docs/domain-rules.md` by section.

## Working agreement

- At the start of every phase: read `CLAUDE.md`, `docs/architecture.md` and `docs/domain-rules.md`, then write a short plan to `docs/plans/phase-N.md` and proceed without waiting for approval, unless something is ambiguous in a way that changes money handling or the locked decisions.
- Work in small vertical slices. After each slice run the fast loop for the touched modules, fix failures, then commit with a conventional commit message (`feat(loan-account): ...`).
- Write the test first for anything involving money, schedules, ledger postings or state transitions.
- Record each non-trivial decision as `docs/adr/NNNN-title.md` (context, decision, consequences). Keep ADRs under one page.
- Never weaken a test, an ArchUnit rule or an enforcer rule to get a build green. Fix the cause.
- Never add a dependency or a service that is not in these documents without an ADR.
- At the end of a phase: run `./mvnw clean verify`, run the phase's end-to-end script, update `README.md` and `docs/runbook.md`, and print the exit checklist with pass or fail for each item.

## Definition of done (every phase)

- `./mvnw clean verify` passes with Spotless, enforcer, ArchUnit and coverage gates.
- `docker compose --profile <needed> up` starts cleanly and every service reports healthy.
- The phase's end-to-end script passes against the running stack.
- OpenAPI specs and Avro schemas are updated, and schema compatibility checks pass.
- No TODO without a linked issue line in `docs/backlog.md`.
- README and runbook describe how to start, test and debug what was built.

## Global do-not list

These apply in every phase. Break one only with an ADR.

- No JPA or Hibernate, no Lombok, no `ThreadLocal`.
- No `double`, `float` or `BigDecimal` for money outside `lms-money`.
- No cross-service database access. No synchronous REST between services other than through the gateway or to simulators.
- No publishing to Kafka except through the outbox. No consumer without an inbox for idempotency.
- No `UPDATE` or `DELETE` on ledger, allocation or state-history rows.
- No `Instant.now()` or `LocalDate.now()` in business code. Use `Clock` and `BusinessCalendar`.
- No unsigned callbacks, no plaintext PII in storage or logs, no secrets in Git.
- No preview Java APIs outside `lms-concurrency`.
- No new dependency, service or topic that is not in these documents without an ADR.
- No weakening of tests, ArchUnit rules or enforcer rules to get green.
- No hard-coded business parameters (rates, thresholds, dunning plans, templates, currency). They come from versioned configuration.
- No scope creep (second product, real providers, real regulatory logic). Add it to `docs/backlog.md`.
