# ADR 0008: Temporal Java SDK 1.40.0 on JDK 25 (smoke test, convergence pins, virtual threads)

- Status: Accepted
- Date: 2026-10-16

## Context

ADR 0001 named the Temporal Java SDK the least-verified dependency on Java 25 and required proof that
a workflow and an activity run on JDK 25 with both virtual-thread options enabled. Phase 3 introduces
the `disbursement` payout saga as a Temporal workflow (CLAUDE.md: Temporal is embedded in
disbursement only). This machine has no container runtime (ADR 0001), so a live Temporal dev server
is deferred — but the SDK ships an in-JVM, time-skipping test server (`TestWorkflowEnvironment`,
`io.temporal:temporal-testing`) that needs no external process.

## Decision / findings

1. **Temporal 1.40.0 runs on JDK 25.** `TemporalSdkSmokeTest` registers a workflow and an activity on
   `TestWorkflowEnvironment`, executes the workflow (including a 30-minute `Workflow.sleep` that the
   test clock skips instantly), and asserts the activity result — green in ~1 s on Oracle JDK 25.0.4.1.
   This is the proof ADR 0001 deferred; the whole disbursement saga is therefore unit-tested in-JVM
   (happy path, callback-after-timer, duplicate/conflict callbacks, unknown outcome, retries,
   `WorkflowReplayer` determinism) without Docker.

2. **Dependency-convergence pins (new).** Temporal's gRPC/protobuf/gson/guava chains disagree on two
   artifacts, which `maven-enforcer`'s `dependencyConvergence` rejects. Pinned in the parent
   `dependencyManagement`, highest/`-jre` flavour:
   - `com.google.errorprone:error_prone_annotations` → `2.50.0` (seen as 2.18.0 / 2.41.0 / 2.50.0).
   - `com.google.guava:guava` → `33.6.0-jre` (seen as 33.4.8/33.6.0, `-android` vs `-jre`).
   This extends the Phase 0 pin list (commons-compress, checker-qual) — no rule was weakened.

3. **Virtual-thread options.** The worker config enables `WorkerOptions.setUsingVirtualThreads(true)`
   (activities) and `WorkerFactoryOptions.setUsingVirtualWorkflowThreads(true)` (workflow threads),
   per CLAUDE.md. The in-JVM test server proves the SDK loads and runs on JDK 25; the two toggles are
   a worker-runtime choice whose end-to-end behaviour is exercised against the **live** Temporal dev
   server in the Docker session (the test server manages its own threading). Virtual threads are never
   pooled; concurrency limits use Resilience4j bulkheads / `Semaphore` (CLAUDE.md).

## Consequences

- No Java-release downgrade; Temporal stays on JDK 25 (ADR 0001's open risk is closed for the SDK
  itself, with the live-server run still to confirm in Docker).
- Services that do not use Temporal keep no Temporal dependency; only `disbursement` (and later
  `collections`) pull `temporal-sdk`.
- If a later SDK changes the virtual-thread API shape or a live-server run contradicts the in-JVM
  result, revisit here.
