# Backlog

Tracked deferrals and TODOs. Every `TODO` in code must have a matching line here.

## Deferred: needs a container runtime (Docker/Compose) — Phase 0

These are authored but cannot run on this machine (no Docker). Run them in a session with Docker and
record results (ADR 0001 for the smoke tests).

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

## Deferred: Money / serde round-trip property

- [ ] The "round-trips through the database and JSON are lossless" money property (domain-rules.md
      "Money type") is currently proven at the value level in `lms-money`. Add the DB round-trip in
      the `customer`/ledger persistence layer and the JSON round-trip in `lms-events` serde.

## Open risks to settle (from ADR 0001)

- [ ] Resilience4j Spring-Boot-4 starter availability (else core modules + explicit config, new ADR).
- [ ] Testcontainers 2.x API vs the authored `*IT` bases.
- [ ] Spring Cloud Gateway coordinates/version for Spring Boot 4.
- [ ] Kafka-clients 4.3.1 vs the Boot-4-managed version (dependencyConvergence override).
