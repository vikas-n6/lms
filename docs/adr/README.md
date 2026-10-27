# Architecture Decision Records

One ADR per non-trivial decision (context, decision, consequences), kept under a page. Record a new
decision before, or with, the change that implements it. Never revisit a locked decision (CLAUDE.md)
without a new ADR.

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-versions.md) | Dependency versions verified on Java 25 | Accepted |
| [0002](0002-build-toolchain.md) | Build toolchain (Maven, wrapper, Java 25) | Accepted |
| [0003](0003-commons-logging-bridge.md) | commons-logging → SLF4J bridge | Accepted |
| [0004](0004-avro-trusted-packages.md) | Avro trusted-packages for deserialization | Accepted |
| [0005](0005-schedule-effective-rate-and-foreclosure.md) | Schedule effective rate and foreclosure | Accepted |
| [0006](0006-preview-flag-for-fanout-consumers.md) | `--enable-preview` for fan-out consumers | Accepted |
| [0007](0007-jooq-without-codegen.md) | jOOQ via the programmatic DSL (no codegen) | Accepted |
| [0008](0008-temporal-on-jdk25.md) | Temporal SDK 1.40.0 on JDK 25 | Accepted |
| [0009](0009-signed-callbacks.md) | HMAC-signed asynchronous callbacks | Accepted |
| [0010](0010-disbursement-saga-rules.md) | Disbursement saga: unknown-outcome / conflict / review | Accepted |
| [0011](0011-collections-dunning-on-dpd-ticks.md) | Collections dunning advances on DPD ticks | Accepted |
| [0012](0012-reconciliation-model.md) | Bank/rail reconciliation model and shared event | Accepted |
| [0013](0013-reporting-read-models.md) | Reporting read models, rebuildable from offset 0 | Accepted |
| [0014](0014-audit-events.md) | Audit events: ownership, building and publishing | Accepted |
| [0015](0015-perf-gc-aot-benchmarks.md) | Performance measurement, GC / compact-headers / AOT method | Accepted |
| [0016](0016-column-key-rotation.md) | Versioned column-encryption keys and rotation | Accepted |
