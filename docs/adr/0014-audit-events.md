# ADR 0014: Audit events — where they are owned, built and published

- Status: Accepted
- Date: 2026-10-27

## Context

Phase 5 task 1 requires that `lms-security` publishes an audit event for every admin action (who,
what, before/after summary, correlation id) on a new `audit.events` topic (keyed by actor id,
docs/architecture.md), and that `reporting` stores them. Two constraints pull against a literal
reading of "lms-security publishes":

- `lms-security` is a dependency-light platform library (JWT, RequestContext, idempotency, PII,
  callback crypto). It depends on neither `contracts`, `lms-events` nor `lms-outbox`. Making it
  depend on all three to build and send Avro would invert the platform layering and risk a cycle
  (`lms-events` already depends on `contracts`).
- The global do-not list forbids publishing to Kafka except through a service's transactional
  outbox, and `lms-security` has no database or outbox of its own.

## Decision

1. **`lms-security` owns the audit *domain and port*, not the transport.** It defines the pure,
   Spring-free `AuditEntry` (actorId, actorRoles, action, targetType, targetId, before/after
   summary, `AuditOutcome`), the `AuditSummary` builder that renders deterministic, **PII-free**
   `k=v; k=v` strings (sensitive values only via `withMasked` + `Pii`), and the `AuditRecorder`
   port. These are unit-tested now (`AuditEntryTest`, `AuditSummaryTest`) with no Docker.

2. **Each service provides the `AuditRecorder` adapter.** A service that performs admin actions
   implements `AuditRecorder` in `adapters.out`: it reads who/trace/correlation from
   `RequestContextHolder`, builds the `audit.events` Avro `AuditEvent`, and appends it to **its own**
   outbox in the same transaction as the audited change. Publishing therefore still goes only through
   the outbox, and the audited change and its audit row commit atomically.

3. **One topic, keyed by actor id.** `AuditEvent` (new schema, so BACKWARD-trivially safe) carries
   the common `EventMetadata` envelope plus actor, action, target, before/after summary and outcome.
   `aggregateId = actorId`, so one actor's actions keep order. `reporting` consumes `audit.events`
   into an append-only `audit_log` read table.

4. **Scope of wiring in this phase.** The mechanism is wired and proven end-to-end in-JVM for the two
   representative loan-account admin actions that Phase 5 adds or exercises: product-configuration
   version creation and write-off approval/rejection. The remaining per-service admin endpoints
   (simulator controls, DLT replay, reconciliation resolution, manual collection actions) are
   retrofitted with the same adapter in the Docker session and tracked in `docs/backlog.md`. No rule
   is weakened: every wired path records before it commits.

## Consequences

- The platform stays free of Kafka/Avro dependencies; the audit contract (domain + port) is shared,
  the transport is per service.
- Summaries are structural by construction (field names, versions, state transitions) and never
  carry raw PII; the no-PII-in-audit-logs acceptance check (Phase 5) verifies this over a live e2e
  run in the Docker session.
- A new audited action is added by constructing an `AuditEntry` and calling `AuditRecorder.record`
  inside the action's transaction — no new topic, no new wiring beyond the service's existing outbox.
