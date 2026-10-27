# Chaos report (Phase 5)

Results of the chaos scenarios (`scripts/chaos/chaos.sh`), each followed by
`scripts/verify-invariants.sh` which must pass. Run in the Docker session; fill in the outcomes.
Expected behaviour (phase prompt task 4): Redis down → idempotency falls back to Postgres, rate
limiting fails open with a metric, caches bypassed; Kafka down → the outbox grows then drains with no
loss or duplication; Postgres down → readiness fails and nothing is lost; Temporal down → workflows
pause and resume.

| Scenario | What it does | Expected behaviour | Invariants | Notes |
| --- | --- | --- | --- | --- |
| kill-kafka | kill + restart Kafka mid-workload | outbox grows while down, drains on recovery; no loss/dup | PASS (TBD) | |
| kill-redis | stop + restart Redis | idempotency → Postgres; rate limit fails open (metric); caches bypassed | PASS (TBD) | |
| kill-temporal | stop + restart Temporal | disbursement/collections workflows pause, then resume | PASS (TBD) | |
| kill-db | stop + start Postgres | service readiness fails; nothing lost | PASS (TBD) | |
| pause | pause + unpause a service mid-flow | in-flight work resumes; no duplicate side effects | PASS (TBD) | |
| duplicate-storm | replay an entire topic into live consumers | inbox dedup absorbs duplicates; no double effect | PASS (TBD) | |
| sim-flaky | all simulators at the `flaky` preset | retries/backoff; eventual consistency; no corruption | PASS (TBD) | |
| sim-down | all simulators at the `down` preset | circuit breakers open; manual-review/park where designed | PASS (TBD) | |

Each row must end with `verify-invariants.sh` reporting zero violations (invariant checks are never
weakened to make a scenario pass — CLAUDE.md / phase prompt "Do not").
