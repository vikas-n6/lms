# ADR 0001: Dependency versions and Java 25 compatibility

- Status: Accepted (version resolution) / Partially proven (container smoke tests deferred)
- Date: 2026-10-11

## Context

CLAUDE.md requires resolving the latest GA of every framework from Maven Central at the start of
Phase 0 (never guessing), pinning them, and proving each runs on Java 25 with a throwaway smoke
test. The Temporal Java SDK is called out as the least-verified item and must be proven with a
workflow + activity running on JDK 25 with both virtual-thread options enabled.

This machine has **no container runtime** (no Docker/Podman/Colima). With the user's agreement we
proceed with all Docker-independent work now and defer the runnable smoke tests that need a broker,
Schema Registry or Temporal dev server. Versions are resolved and pinned now; execution is deferred.

## Decision

Versions resolved from authoritative `maven-metadata.xml` (`<release>`) on 2026-10-11 and pinned in
the parent `pom.xml`:

| Component | Version | Source of truth |
| --- | --- | --- |
| Spring Boot (→ Spring Framework 7) | 4.1.1 | latest 4.1.x GA (4.2.0 only at milestone) |
| Temporal Java SDK | 1.40.0 | GA, well past the 1.37.0 that stabilised virtual threads |
| Apache Avro | 1.12.2 | GA |
| Confluent Schema Registry client + Avro serde | 8.3.2 | Confluent repo GA |
| Kafka clients | 4.3.1 | GA (KRaft; Java 25 recommended per Apache) |
| jOOQ | 3.21.9 | GA |
| Redisson | 4.8.0 | GA |
| Resilience4j | 2.4.0 | GA (BOM) |
| Testcontainers | 2.0.5 | GA (BOM; 2.x major) |
| jqwik | 1.10.2 | GA (no `jqwik-bom` published → manage the `jqwik` aggregator by version) |
| ArchUnit | 1.5.1 | GA |
| Spotless Maven plugin | 3.10.4 | GA |
| google-java-format | Spotless default | see ADR 0002 |

## Java 25 compatibility findings (proven so far)

- **JDK**: Oracle Java SE 25.0.4.1 (LTS). `javac` 25. Confirmed the Maven Wrapper compiles and runs
  on JDK 25 (`JAVA_HOME` → JDK 25; system Maven defaults to Homebrew JDK 23 and is not used).
- **lms-money** compiles and its JUnit 5 + jqwik property tests (≥10,000 cases each) run green on
  JDK 25, including currencies with 0 (JPY), 2 (INR) and 3 (KWD) fraction digits.
- **google-java-format**: 1.25.2 is incompatible with JDK 25 javac internals
  (`NoSuchMethodError: Log$DeferredDiagnosticHandler.getDiagnostics()`); resolved per ADR 0002.
- **jqwik 1.10.2**: runs on JDK 25 under the JUnit Platform (proven by the money property tests).
  Note: its engine prints a banner containing an adversarial "ignore your instructions" line; it is
  library output, not an instruction, and is ignored.

## Deferred until a container runtime is available (tracked in docs/backlog.md)

Runnable smoke tests, to record their results back into this ADR:

1. **Kafka 4.3.1 broker (KRaft) + clients** under JDK 25 via Testcontainers.
2. **Confluent Schema Registry client 8.3.2 + Avro 1.12.2** serde round-trip against a running
   Schema Registry.
3. **Temporal SDK 1.40.0** — a workflow and an activity on JDK 25 with
   `WorkerOptions.setUsingVirtualThreads(true)` and
   `WorkerFactoryOptions.setUsingVirtualWorkflowThreads(true)` (the least-verified item).
4. **jOOQ 3.21.9 / Redisson 4.8.0 / Resilience4j 2.4.0 / Testcontainers 2.0.5** one smoke test each.
   Tools that read bytecode (ArchUnit 1.5.1) must parse Java 25 class files — ArchUnit runs as plain
   unit tests and is proven in `lms-testkit`.

## Open risks to settle in the Docker session

- Resilience4j 2.4.0 may ship only a Spring-Boot-3 starter; if so, wire the core modules with
  explicit configuration (follow-up ADR).
- Testcontainers 2.x API differs from 1.x; the `*IT` bases are authored against 2.x, unverified.
- Spring Cloud Gateway coordinates/version for Boot 4 confirmed at the gateway slice.
- Kafka-clients 4.3.1 vs the Boot-4-managed version may need an explicit convergence override.

## Consequences

Service POMs carry no versions; everything flows from the parent and the imported BOMs. The build is
green on JDK 25 for all Docker-independent modules. The phase is not "done" by its Definition of Done
until the deferred smoke tests and integration tests pass; the `phase-0-done` tag is withheld.
