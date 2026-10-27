# ADR 0015: Performance measurement, GC / compact-headers / AOT benchmark method

- Status: Accepted
- Date: 2026-10-27

## Context

Phase 5 task 3 requires measured performance before any optimisation, JFR profiling of the top
bottlenecks, and a benchmark of G1 vs ZGC vs Generational Shenandoah, compact object headers on/off,
and the AOT cache — adopting the winner per service in the container images and recording each
choice. CLAUDE.md defaults the collector to G1 until Phase 5 and gates several Java-25 features
(compact headers, AOT cache, JFR) behind a "benchmark first, record in an ADR" rule. This machine
has no container runtime, so the runs happen in the Docker session; this ADR fixes the method so the
results are comparable and no premature tuning ships.

## Decision

1. **Measure before optimise (enforced by order).** The k6 scripts in `scripts/perf/` establish the
   baseline first: `app-to-decision.js` (decision p95 < 3 s at 50 concurrent), `repayment-callback.js`
   (ack p95 < 200 ms at 500 rps), `gateway-mixed-reads.js` (error rate < 0.1%). Numbers and the
   environment go in `docs/perf/` before any change. No optimisation is committed without a
   before/after pair.

2. **Profile with JFR, fix only the top bottlenecks.** A `jfr` compose profile starts services with
   `-XX:StartFlightRecording=...` (method timing + tracing); the recipe is in `docs/perf/README.md`.
   Only the few bottlenecks the recording actually shows are addressed; each fix records before/after.

3. **GC / compact-headers / AOT matrix, per service.** For each service, measure startup time, steady
   p95 and RSS under the mixed-read load across: G1 (baseline), ZGC (`-XX:+UseZGC
   -XX:+ZGenerational`), Generational Shenandoah (`-XX:+UseShenandoahGC`); `-XX:+UseCompactObjectHeaders`
   on vs off; and the AOT cache (JEP 514/515: a training run then `-XX:AOTCache=app.aot`) on vs off.
   The winner per service is set in that service's Jib `jvmFlags`, and the chosen flags are recorded
   in a short follow-up note appended to this ADR. Latency-sensitive request services likely favour a
   generational low-pause collector; batch/daily-job services may keep G1 — decided by the numbers,
   not by assumption.

4. **Preview/flag discipline unchanged.** Compact headers and the AOT cache are product flags toggled
   only in the container images after the recorded benchmark (CLAUDE.md). `--enable-preview` is not
   involved (these are final/product features, not preview APIs).

## Consequences

- The perf scripts and the `jfr` profile are authored now; the baseline run, JFR profiling, the
  GC/headers/AOT matrix and the per-service image flag changes are the Docker session's to produce,
  with results in `docs/perf/` and the winners appended here (backlog).
- Until those runs exist, every image stays on the G1 default — no speculative GC or header flags are
  shipped, honouring "do not optimise anything before measuring it".
