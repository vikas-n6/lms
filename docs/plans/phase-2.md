# Phase 2 plan: Loan core and ledger

Status key: [ ] todo · [~] in progress · [x] done · (D) needs the Docker session to verify at runtime.

## Gate (done before any work)

- [x] Read CLAUDE.md, architecture.md, domain-rules.md, ADRs 0001–0006.
- [x] `phase-1-done` tag exists (created at the start of this session, on the user's instruction, in
      the same "unblock sequencing" style as `phase-0-done`; the container/e2e verification Phase 1
      deferred stays deferred to the final Docker session — backlog unchanged).
- [x] `./mvnw clean verify` green on JDK 25 (exit 0): Spotless, enforcer, ArchUnit, coverage,
      jqwik, Avro serde via MockSchemaRegistry. No Docker.

## Guiding constraints (locked decisions that shape every slice)

- **No Docker on this machine** (memory + ADR 0001): all Testcontainers ITs, the jqwik *stateful*
  ledger property test if it needs a DB, docker compose, `scripts/e2e/phase2.sh`, the 12-month
  simulated-months UI check, concurrency/replay ITs are **authored now, executed in the final
  Docker session**. Everything provable with pure unit / property (jqwik) / slice / ArchUnit tests
  under `./mvnw clean verify` is proven now and must stay green every commit.
- **Money & schedule maths live only in `lms-money` / `lms-schedule`** (both already built, 0.90
  coverage). Phase 2 adds *no* new BigDecimal anywhere else (ArchUnit enforces). loan-account and
  ledger compose `Money`/`Rate`/`Amortization`/`ScheduleEngine` results; they never do raw maths.
- **No stored mutable balances in the ledger.** Balances are derived by summing postings. No
  `UPDATE`/`DELETE` granted on `accounts`/`journal_entries`/`postings` (role grants in postgres-init,
  verified in the Docker session; domain objects are immutable now).
- **jOOQ for the ledger and money paths** (locked). jOOQ codegen needs a live Postgres, which is
  deferred — so the ledger uses the **jOOQ DSL programmatically** (`DSL.using(Configuration)` with
  `DSL.table`/`DSL.field`, no generated classes). Recorded in an ADR. loan-account uses Spring Data
  JDBC / `JdbcClient` like the other services (locked: "jOOQ for the ledger and money paths, Spring
  Data JDBC elsewhere").
- **State machine, allocation, accrual, posting templates** are pure functions in `domain` (no
  Spring, no clock, no I/O): tests first, exhaustive sealed `switch` with no `default`.
- **Business dates** come from a shared `BusinessCalendar`, never `LocalDate.now()`. Jobs take the
  business date as a parameter. `Clock` injected for UTC instants on events/rows.
- Outbox for every publish, inbox for every consume (idempotent on `eventId`; ledger also idempotent
  on `sourceEventId`). Per-aggregate Kafka key = loanId (loan/ledger events keyed so ordering holds).

## Slices (in order). Each: tests first for money/schedule/state/ledger, fast-loop the touched
## modules, then a conventional commit.

### S1 — `BusinessCalendar` in `lms-config` [ ]
- `BusinessCalendar` interface: `LocalDate today()` (business date, `Asia/Kolkata`).
- `ClockBusinessCalendar` default impl (from the injected `Clock`, zoned to `Asia/Kolkata`) — pure,
  unit-testable with a fixed clock.
- `RedisBusinessCalendar` reads `lms:platform:business-date` (ISO date), falls back to the clock
  value. Guarded `@ConditionalOnClass(StringRedisTemplate)` + dev profile so pure modules and
  non-Redis services still build. Unit test with a fake value source.
- Auto-configure the default bean in `LmsConfigAutoConfiguration` (`@ConditionalOnMissingBean`).
- A small `BusinessDateJobs` contract (`POST /admin/jobs/{name}/run?date=`) is added per service in
  its own slice; the calendar lives here. **Tests now**; Redis read path verified (D).

### S2 — contracts: new Avro events [ ]
- Add `.avsc` (shared envelope + `MoneyAmount`) for: `loan.created`, `loan.activated`,
  `loan.interest-accrued`, `loan.repayment-allocated`, `loan.closed`, `loan.cancelled`,
  `ledger.entry-posted`, `ledger.entry-reversed`. Author `disbursement.initiated/succeeded/failed`
  and `repayment.received` schemas too (consumed in P2, produced in P3) so templates/listeners
  compile; producers arrive in Phase 3.
- Serde round-trip unit tests (MockSchemaRegistry, as in Phase 1). Schema-registry BACKWARD compat
  check = (D) via `scripts/check-schema-compat.sh`.

### S3 — `loan-account` domain (pure, tests first) [ ]
- `LoanState` sealed interface: `Created`, `PendingDisbursal`, `Active`, `Delinquent`, `Closed`,
  `Foreclosed`, `WrittenOff`, `Cancelled`. `LoanTransition` sealed trigger type. Pure
  `(state, trigger) -> Result<LoanState, IllegalTransition>` with exhaustive `switch`, guards exactly
  per domain-rules "Loan state machine". Illegal transition returns the error (audited by caller,
  state unchanged). **Test: every legal + every illegal transition.**
- `DpdBucket` enum + `dpdOf(oldestUnpaidDueDate, businessDate)` (CURRENT/1_30/31_60/61_90/NPA).
- `ScheduleItemState` enum (UPCOMING, DUE, PARTIALLY_PAID, PAID, OVERDUE) + per-item paid-component
  tracking (`LoanScheduleItem` domain record: scheduled vs paid principal/interest/penal).
- `AllocationWaterfall`: pure function (schedule items, payment `Money`, businessDate) ->
  `AllocationResult` (list of `Allocation(scheduleItemId, component, amount)` + advance remainder).
  Order: oldest overdue first, within an instalment penal→interest→principal; then current
  instalment; then advance to suspense. **Property test (jqwik): allocated + remaining == payment,
  deterministic replay; never over-allocates a component.**
- `InterestAccrual`: spread an instalment's scheduled interest evenly across its period days, last
  day takes the remainder (use `Money.allocate` over day weights). Penal accrual via
  `Amortization.simpleInterest(base, penalAPR, days, 365, HALF_UP)`. **Property test: sum over the
  12-month life equals scheduled interest within 1 minor unit/instalment.**
- `Balances` derivation (outstanding principal, accrued interest, penal, total due now, next due).

### S4 — `loan-account` service wiring (authored; runtime = D) [ ]
- Scaffold via `new-service.sh loan-account 8104`, then rewrite for the loan aggregate.
- Flyway V0100: `products` (seeded with the one product + version), `loans`, `schedule_items`,
  `accruals` (unique `(loan_id, business_date, kind)`), `allocations`, `loan_state_history`
  (append-only), plus the shared outbox/inbox location. Loan number from a DB sequence,
  `LN-2026-000001`. Every table `created_at/updated_at/version`.
- Consume `application.accepted` idempotently (unique on `applicationId`): create loan `Created`,
  pin product version + accepted terms, draft provisional schedule (`lms-schedule`), →
  `PendingDisbursal`, publish `loan.created`.
- Consume `disbursement.succeeded` (guarded: amount == principal − fee − GST): real schedule from the
  actual date, persist `schedule_items`, → `Active`, publish `loan.activated`. Dev endpoint
  `POST /admin/loans/{id}/simulate-disbursement` until Phase 3.
- Consume `repayment.received`: run `AllocationWaterfall` in one tx, write `allocations`, update
  item states, publish `loan.repayment-allocated`. Dev endpoint to inject `repayment.received`.
- Daily interest accrual job (idempotent per `(loanId, businessDate)`), publish
  `loan.interest-accrued`. `POST /admin/jobs/interest-accrual/run?date=`.
- Close on zero outstanding → `loan.closed`.
- Read APIs: list/filter loans; detail with balances; schedule; state history;
  `GET /loans/{id}/foreclosure-quote?date=`.
- ArchUnit test (hexagonal + no ThreadLocal/BigDecimal). Web-slice + application tests with fakes.

### S5 — `ledger` service [ ]
- Scaffold via `new-service.sh ledger 8108`, rewrite for double-entry.
- Domain (pure, tests first): `Account`, `JournalEntry` (balances per currency — rejects unbalanced
  in the constructor), `Posting`, `PostingTemplate` interface + versioned sealed templates:
  `disbursement.initiated`, `disbursement.succeeded`, `disbursement.failed`, `loan.interest-accrued`.
  Repayment/penal/write-off templates left as the interface's open set (Phases 3–4).
- Flyway V0100: `accounts` (seeded chart of accounts), `journal_entries`, `postings` (with `loan_id`,
  `customer_id`), unique index on `source_event_id`, **deferred constraint trigger** rejecting an
  unbalanced entry at commit. No UPDATE/DELETE grant (role in postgres-init; (D) to prove).
- jOOQ DSL persistence (no codegen — ADR). Consume loan + disbursement events idempotently via inbox;
  publish `ledger.entry-posted`.
- `POST /journal-entries/{id}/reverse` (admin, reason required): mirrored entry with
  `reversesEntryId`; reversing twice rejected.
- Queries: entries (filter loan/date/account), entry detail, account balance as-of, trial balance,
  per-loan sub-ledger. Scheduled integrity job: trial balance nets to zero; export
  `ledger_trial_balance_diff_minor_units`; publish alert if non-zero.
- **jqwik stateful property test** of the ledger invariants: random accrual/allocation/reversal
  sequences keep trial balance zero, no must-be-non-negative account negative, per-loan receivables
  track loan balances. Runs against the in-memory domain (no DB) so it is provable now; the SQL
  constraint/replay/concurrency variants are (D).

### S6 — gateway routes + roles [ ]
- Routes for `/loans/**` and `/ledger/**` (and `/journal-entries/**`), read for `analyst`, admin for
  reverse/jobs/business-date. Dev-only `PUT /admin/business-date` to set/advance the Redis key.

### S7 — admin UI pages [ ] (build under `-Pui`; OIDC login = D)
- Loans list + detail (state badge, balances, state-history timeline). EMI schedule viewer
  (per-item status colours, paid components, overdue highlight). Ledger explorer (entries, postings,
  account balances, trial balance + zero-check badge, loan→entries drill-down). Foreclosure-quote
  dialog. Dev-only business-date widget in the header.

### S8 — e2e + wiring [ ]
- `scripts/e2e/phase2.sh` (create→simulate-disbursement→accrue months→repay→allocate→close; trial
  balance zero; foreclosure quote; replay). compose blocks for loan-account + ledger, postgres-init
  DBs/roles (ledger role without UPDATE/DELETE). runbook + README updates. Running it = (D).

## ADRs to record
- jOOQ used via the programmatic DSL (no codegen) in the ledger, because codegen needs a live DB that
  is deferred; honours the locked "jOOQ for the ledger" decision.
- Interest-income daily recognition method (even spread across period days, last day takes the
  rounding remainder) and penal actual/365 — confirm it reconciles to scheduled interest within the
  stated tolerance; cross-reference ADR 0005 (same day-count family).
- Ledger immutability enforced by a deferred constraint trigger + withheld UPDATE/DELETE grants
  (how, and how it is proven in the Docker session).

## Risks / open questions
- **jOOQ without codegen**: the DSL-only approach loses compile-time column typing. Mitigate with a
  thin hand-written table-constants class and tests. If this proves unworkable in the Docker session,
  switch to codegen there and record a follow-up ADR. *No money-handling or locked-decision change.*
- **`disbursement.succeeded` guard** (amount == principal − fee − GST): Phase 3 produces the event;
  Phase 2 asserts the invariant and exercises it via the dev simulate endpoint. The net-amount
  identity is already proven in `lms-schedule` (`OfferQuote`), reused here — no new money maths.
- **Interest recognition vs instalment interest**: domain-rules spreads *scheduled* interest across
  calendar days for income recognition; the schedule's per-instalment interest is the reducing-
  balance figure. Recognition must sum back to the scheduled interest (tolerance per instalment).
  This is a recognition-timing choice, not a change to any customer-owed amount — ADR, no clarify.
- **Stateful jqwik against in-memory domain vs DB**: the invariant test runs on the domain now; the
  SQL trigger / replay-from-offset-0 / 50-concurrent variants are real ITs, deferred to Docker.
- Heavy infra (Kafka/Redis/Postgres) stays deferred; keep every slice independently build-green.

## Definition of done (now vs Docker)
- Now: `./mvnw clean verify` green; tests-first for state machine, allocation, accrual, posting
  templates, ledger invariants (jqwik), schedule reuse; Avro serde round-trips; ADRs for deviations;
  90% coverage holds on `lms-money`/`lms-schedule`.
- Docker session (D): `scripts/e2e/phase2.sh`, unbalanced-entry SQL constraint, replay-from-0,
  50-concurrent, accrual-idempotency-at-DB, schema BACKWARD compat, UI with OIDC + 12 simulated
  months, no-UPDATE/DELETE grant proof. Tag `phase-2-done` only after those pass (mirrors the
  discipline the prior phases set, now that `phase-1-done` is tagged to unblock sequencing).
