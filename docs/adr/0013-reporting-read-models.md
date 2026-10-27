# ADR 0013: Reporting read models — event-sourced, rebuildable from offset 0

- Status: Accepted
- Date: 2026-10-27

## Context

Phase 5 task 1 adds a `reporting` service (port 8110) that must provide portfolio snapshots, an
application funnel, collection efficiency and vintage curves, store the audit trail, and be
"rebuildable by replaying from offset 0". docs/architecture.md already lists it and forbids any
service reading another service's database. Reporting handles money amounts (outstanding balances)
but is not a money *authority* — it holds denormalised copies derived from events, not postings or
schedules.

## Decision

1. **Read models are pure projections of the event stream.** `reporting` consumes every domain topic
   (`loan.events`, `application.events`, `decision.events`, `disbursement.events`,
   `collections.events`, `ledger.events`, `audit.events`) with the consumer group `reporting` and
   `auto.offset.reset=earliest`. It owns only its read-model database (`loan_read`,
   `application_read`, `collection_counter`, `audit_log`, `daily_snapshot`, `collection_case_read`,
   `recon_status`) and reads no other service's data (ArchUnit keeps the domain Spring-free; there is
   no JDBC path to any other service's tables). A **rebuild** is therefore: reset the group's offsets
   to 0, truncate the read tables, and replay — the projections are deterministic functions of the
   ordered stream, so the rebuilt figures equal the live ones (the Phase 5 acceptance check).

2. **Each projection is load-modify-upsert and idempotent.** Every listener runs inside one
   transaction that also marks the `eventId` in `inbox_processed` (the shared inbox), so a duplicate
   or replayed delivery is skipped. Funnel stages are OR-merged (a flag only moves false→true) and
   terminal loan states are pinned, so out-of-order or repeated events never regress a row.

3. **Aggregation math is pure and unit-tested, with no BigDecimal/float.** Portfolio, funnel,
   collection-efficiency and vintage figures are computed by pure domain functions over the read
   rows. Ratios are integer **basis points** (`part*10000/whole`, 0 on a zero denominator) — reporting
   carries no `BigDecimal` or floating point (CLAUDE.md; rounding lives only in lms-money). Money sums
   use `lms-money` arithmetic. All of this is tested now without Docker (`Ratios`, `PortfolioMetrics`,
   `ApplicationFunnel`, `CollectionEfficiency`, `VintageCurve`, and the projection tests).

4. **Documented metric definitions (event-sourced proxies).**
   - *NPA ratio* = outstanding principal of NPA-bucket loans ÷ total outstanding principal (bps). A
     gross-NPA-to-gross-advances proxy on the live book, not a 90-day lagged average.
   - *Collection efficiency* = collected ÷ billed (bps), where "billed" accumulates instalments
     becoming due (`loan.installment-due-soon`) plus penal charged (`loan.penal-accrued`), and
     "collected" accumulates allocation totals (`loan.repayment-allocated`, net of advance), reversed
     on `loan.repayment-reversed`.
   - *Vintage* = per disbursal-month cohort, the share 30+ DPD (`DPD_31_60` or worse), bps.
   - *Loan state* is inferred from the stream (no explicit state event is published): DPD decides
     Active vs Delinquent, terminal events pin Closed/Foreclosed/WrittenOff/Cancelled.

5. **Daily snapshots** are keyed by the business date from the shared `BusinessCalendar` (not
   wall-clock) and upserted, so the series follows simulated business days and a re-run is idempotent.

6. **Persistence.** Spring Data JDBC (`JdbcClient`), not jOOQ: reporting is "elsewhere" in the
   CLAUDE.md split (jOOQ is for the ledger/money authority). Money columns are bigint minor units + a
   currency column.

## Consequences

- The live replay-from-0 equality check, the dashboards' SSE wiring and the figures against a running
  stack are the Docker session's to run; the projection and aggregation logic is fully proven in-JVM.
- Adding a metric means adding a pure function over the existing read rows; adding a projected field
  means a migration plus a handler, both replay-safe.
- Because reporting is a consumer only, it has an inbox but no outbox and produces no events.
