# ADR 0007: jOOQ used via the programmatic DSL (no code generation) in the ledger

- Status: Accepted
- Date: 2026-10-14

## Context

CLAUDE.md locks "jOOQ for the ledger and money paths, Spring Data JDBC elsewhere". jOOQ's usual
workflow generates typed table/record classes from a live database schema at build time (the
`jooq-codegen` plugin connects to Postgres). This machine has no container runtime (ADR 0001), so
code generation cannot run in the build, and the ledger service is being authored now with its
runtime verification deferred to the Docker session.

## Decision

Use jOOQ in the ledger service through its **programmatic DSL** (`DSLContext` with
`DSL.table(...)`/`DSL.field(...)` and inline SQL), not generated classes. This keeps the locked
"jOOQ for the ledger" decision: the ledger's money-path persistence is written in jOOQ, type-checked
at the query-builder level, and runs on the same `DataSource` as Flyway. `spring-boot-starter-jooq`
provides the managed jOOQ version and auto-configures a `DSLContext`.

- Table and column names are centralised in a small hand-written constants holder so the DSL has a
  single source of truth and typos surface in one place.
- loan-account and the other services keep Spring Data JDBC / `JdbcClient` (the locked default).

If, in the Docker session, generated classes prove worth the extra build wiring (a Testcontainers-
backed codegen step), that is an additive change recorded in a follow-up ADR; nothing in the ledger
domain depends on either choice (the domain is pure and jOOQ-free; only the out-adapter uses jOOQ).

## Consequences

- No build-time database dependency; the ledger builds green without Docker.
- The DSL loses generated compile-time column typing; mitigated by the constants holder and the
  jqwik/unit tests over the pure domain, plus the Docker-session integration tests (unbalanced-entry
  constraint, replay-from-offset-0, concurrency).
- This is not a change to any money rule or stored amount — it is a persistence-mechanism choice.
