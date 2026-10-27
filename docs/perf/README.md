# Performance results (Phase 5)

Measure before optimising (ADR 0015). This folder holds the baseline and before/after numbers from
the Docker session; the scripts live in `scripts/perf/` (k6). Until those runs exist, every image
ships on the G1 default — no speculative GC/header/AOT flags.

## Targets (developer laptop)

| Flow | Target | Script |
| --- | --- | --- |
| Application → decision | p95 < 3 s at 50 concurrent | `scripts/perf/app-to-decision.js` |
| Repayment callback ack | p95 < 200 ms at 500 rps | `scripts/perf/repayment-callback.js` |
| Mixed gateway reads | error rate < 0.1% | `scripts/perf/gateway-mixed-reads.js` |

## How to run

```
# core + auth + apps up, images built (jib). Example:
k6 run -e BASE=http://localhost:8080 -e KC=http://localhost:8180 scripts/perf/app-to-decision.js
```

## JFR profiling

A `jfr` compose profile starts services with method timing + tracing:
`-XX:StartFlightRecording=filename=/tmp/app.jfr,settings=profile,dumponexit=true`. Pull the `.jfr`
with `docker compose cp`, open in JDK Mission Control, and address only the top bottleneck(s). Record
a before/after pair here for each change.

## Baseline (fill in from the Docker run)

| Date | Flow | p95 | throughput | error rate | notes |
| --- | --- | --- | --- | --- | --- |
| _TBD_ | app→decision | | | | baseline |
| _TBD_ | repayment callback | | | | baseline |
| _TBD_ | mixed reads | | | | baseline |

## GC / compact-headers / AOT matrix (per service)

Record startup time, steady p95 and RSS for each combination; adopt the winner in that service's Jib
`jvmFlags` and append the chosen flags to ADR 0015.

| Service | Collector | Compact headers | AOT cache | Startup | p95 | RSS | Adopted |
| --- | --- | --- | --- | --- | --- | --- | --- |
| _TBD_ | G1 / ZGC(gen) / Shenandoah(gen) | on/off | on/off | | | | |
