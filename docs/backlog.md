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

- [ ] customer: extend the aggregate (encrypted id number + bank account columns via FieldCipher,
      keyed BlindIndex lookups), KYC submission via Resilience4j to the KYC sim + async callback
      endpoint, bank linking + penny-drop, append-only consents, Redis `OtpStore`, publish
      `customer.registered` / `customer.kyc.completed` / `customer.bank-account.linked` via the
      outbox; REST + JDBC + Flyway `customers`/`kyc_records`/`consents`/`bank_accounts`.
- [ ] origination: REST (create/update/submit/get/list+filter+page/withdraw/accept-with-OTP-esign),
      JDBC `applications`/`application_events`/`offers`, consume `decision.completed` → build offer
      (OfferPricing) or reject, 48h offer expiry scheduled job emitting `application.expired`,
      outbox publishing of `application.*`, CompliancePolicy calls recorded before offer + accept.
- [ ] decision: consume `application.submitted` idempotently (inbox), HTTP BureauClient/BankClient to
      the simulators behind Resilience4j (retry/circuit-breaker/time-limiter), Redis velocity sliding
      window (Lua), persist decisions + bureau snapshots + inputs + per-rule results, Redis rule-set
      cache keyed by version + JDBC `rule_sets` seeded from `rule-sets/v1.json`, REST
      `GET /decisions/{id}/explain` and `POST /decisions/{id}/review` (admin), publish
      `decision.completed`. Container `JAVA_TOOL_OPTIONS` must include `--enable-preview` (ADR 0006).
- [ ] gateway: routes for customers/applications/decisions/simulator-control (admin only); role
      mapping admin=all, analyst=read, customer=own records.
- [ ] notification stub: consume `application.*` + `customer.*`, log via the SMS sim.
- [ ] admin UI: Customers, Applications (+event timeline), Decision explainer, Manual review queue,
      New-application wizard, Simulator control panel (+live SMS outbox).
- [ ] `scripts/e2e/phase1.sh`: personas 9000 (approve+accept), 9002 (reject BUREAU_SCORE_LOW), 9003
      (refer→admin override→offer), 9004 (reject BUREAU_DELINQUENT); 4-apps/24h velocity breach;
      bureau 100% failure → REFER/BUREAU_UNAVAILABLE within 10s; duplicate `application.submitted` →
      one decision; replay of a stored decision; offer figures match the engine.
- [ ] k6 decision-latency script (p95 < 3s at 20 concurrent, informational).
- [ ] compose service blocks (customer/origination/decision/simulators/notification) + postgres-init
      roles/DBs; Schema Registry BACKWARD compatibility check for the new events; README + runbook.
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
