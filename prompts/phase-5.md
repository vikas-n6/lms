# Phase 5 prompt (paste this whole file into Claude Code)

You are working in the `lms` repository on Phase 5.

1. Read CLAUDE.md, docs/architecture.md, docs/domain-rules.md and every ADR in docs/adr/.
2. Confirm the git tag phase-4-done exists and that `./mvnw clean verify` passes.
   If either fails, stop and tell me instead of continuing.
3. Write your plan to docs/plans/phase-5.md (tasks in order, risks, open questions),
   then start work. Ask me only if something changes money handling or conflicts with
   a locked decision.
4. Work in small vertical slices. After each slice run the fast loop for the touched
   modules, fix failures, and commit with a conventional commit message.
5. Write tests first for money, schedules, ledger postings and state transitions.
6. Record non-trivial decisions as ADRs. Never weaken a test or rule to get green.
7. At the end run `./mvnw clean verify` and the phase e2e script, then print the exit
   checklist with pass or fail for each item.

The phase specification follows.

---

## Phase 5: Hardening

**Goal:** measured performance, proven failure behaviour, reporting read models, a security pass and operator-grade documentation, without adding new business features.

**Read first:** `CLAUDE.md`, all docs, every ADR, and the Phase 4 e2e script. Write the plan to `docs/plans/phase-5.md`, then proceed.

### Tasks

1. **`reporting` service (new, port 8110, ADR required).** Consume every domain topic into denormalised read tables, rebuildable by replaying from offset 0. Provide daily portfolio snapshots (active loans, outstanding principal, disbursed amount, DPD distribution, NPA ratio), an application funnel (submitted, approved, offered, accepted, disbursed), collection efficiency, and vintage curves by disbursal month. Add the `audit.events` topic: `lms-security` publishes an audit event for every admin action (who, what, before and after summary, correlation id), and `reporting` stores them.
2. **Admin UI additions.** **Dashboards** from `reporting` (portfolio, funnel, DPD distribution, collection efficiency) with live status updates through SSE (Redis pub/sub fanned out by the gateway). **Product configuration** page: view the current product version, create a new effective-dated version, and show that existing loans keep their original version. **Audit log** page with filters. A **DLT viewer** with replay for each dead-letter topic.
3. **Performance work, measured first.**
    - k6 scripts in `scripts/perf/`: application to decision flow, repayment callbacks, mixed read traffic through the gateway.
    - Targets on a developer laptop: decision p95 below 3 s at 50 concurrent applications, repayment callback acknowledgement p95 below 200 ms at 500 requests per second, gateway error rate below 0.1%.
    - Profile with JFR (method timing and tracing). Fix the top bottlenecks only, and record before and after numbers in `docs/perf/`.
    - Benchmark G1, ZGC and Generational Shenandoah, compact object headers on and off, and the AOT cache on startup time and memory. Adopt what wins per service in container images and record each choice in an ADR.
4. **Chaos scenarios** in `scripts/chaos/`, each followed by `scripts/verify-invariants.sh`:
    - Kill and restart Kafka, Redis, Temporal, and each service's database, during a running lifecycle workload.
    - Pause service containers mid-flow. Replay entire topics into running consumers (duplicate storm). Run every simulator at the `flaky` and `down` presets.
    - Expected behaviour: Redis down means idempotency falls back to Postgres, rate limiting fails open with a metric, and caches are bypassed. Kafka down means the outbox grows then drains with no loss or duplication. Postgres down means readiness fails and nothing is lost. Temporal down means workflows pause and resume.
    - Write results to `docs/chaos-report.md`.
5. **Invariant checker** `scripts/verify-invariants.sh`: the trial balance is zero; each loan's principal, interest and penal balances equal the sum of its schedule items and equal its ledger sub-ledger; no loan is stuck in `PendingDisbursal` beyond a threshold; every outbox is drained; no duplicate `eventId` was processed twice; each loan has at most one successful disbursement; DLT topics are empty or explained.
6. **Soak test.** A script that creates 1,000 loans and runs them through compressed full lifecycles with a realistic mix of on-time, late, bounced, foreclosed and defaulted behaviour. Run the invariant checker at the end. Zero violations is required.
7. **Security review.** Write `docs/threat-model.md` (short STRIDE per service boundary). Add dependency scanning, secret scanning and image scanning to CI. Verify that service-to-service REST requires client-credentials tokens, and document an optional mTLS profile. Audit logs from a full e2e run for PII leakage. Document the column-encryption key rotation procedure and test it on a copy of the data.
8. **Kubernetes-readiness check** `scripts/check-k8s-readiness.sh`: verifies probes, non-root users, environment-only configuration and graceful shutdown (a SIGTERM during a request lets it finish). Do not write Helm charts.
9. **Operator documentation.** `docs/runbook.md` with one section per failure (stuck disbursement, DLT growth, outbox lag, ledger imbalance alert, reconciliation mismatch, Temporal worker down), Grafana dashboards for service health, Kafka lag, outbox lag, DLT counts, Temporal failures and the ledger trial balance metric, an ADR index, an updated architecture diagram, and a Bruno or Postman collection generated from the OpenAPI specs.

### Acceptance tests

- Performance targets above are met, or each miss has a written explanation and a backlog item.
- Every chaos scenario ends with `verify-invariants.sh` passing.
- The soak test of 1,000 loans completes with zero invariant violations.
- Rebuilding `reporting` from offset 0 yields identical figures to the live read models.
- CI fails on a deliberately introduced high-severity vulnerability and on a planted secret (prove it once, then remove).
- No PII appears in e2e logs.
- Changing the product configuration creates a new version and leaves existing loans on their original version.

### Do not

- Do not optimise anything before measuring it.
- Do not add business features. New ideas go to `docs/backlog.md`.
- Do not weaken invariant checks to make a chaos scenario pass.
- Do not write Helm charts or Kubernetes manifests.

### Exit checklist

- [ ] `./mvnw clean verify` green and CI green
- [ ] All performance, chaos, soak and security items pass or are explicitly documented
- [ ] Runbook, ADR index and architecture diagram are current
- [ ] Tag `phase-5-done` and `v1.0.0`
