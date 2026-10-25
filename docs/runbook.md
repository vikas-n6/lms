# Runbook

How to start, stop, reset and debug the LMS locally. Phase 0 scope.

## Start / stop / reset

```bash
cp deploy/.env.example deploy/.env     # once
make up-core      # infra only (Postgres, Kafka, Schema Registry, Redis, Kafka UI)
make up-auth      # + Keycloak
make up-full      # everything
make down         # stop containers (keep data)
make reset        # stop + delete volumes (fresh databases/topics)
```

Build artifacts and images:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
make build        # ./mvnw clean verify
make images       # Jib builds lms/customer, lms/gateway images
```

## Where to look when things fail

| Symptom | Where to look |
| --- | --- |
| A service won't start | `docker compose -f deploy/docker-compose.yml logs <svc>`; its `/actuator/health` |
| Readiness never turns UP | DB reachable? migrations applied? `logs customer` for Flyway errors |
| Event not delivered | Kafka UI (http://localhost:8089): topic `customer.events`; the `outbox` table (`published_at`) |
| Schema rejected | `scripts/check-schema-compat.sh`; Schema Registry at :8081 (`/subjects`) |
| 401 from the gateway | token expired / wrong issuer; check `OIDC_ISSUER_URI` and Keycloak realm `lms` |
| 403 from the gateway | user lacks `ADMIN`/`ANALYST` realm role (see the realm users) |
| 429 from the gateway | rate limit hit (100/min per client in Phase 0) |
| Metrics/traces missing | OTel Collector logs; Grafana datasources (Prometheus/Loki/Tempo) |

Useful endpoints: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`.

## Inspecting data

```bash
# Outbox state for the customer service
docker compose -f deploy/docker-compose.yml exec postgres \
  psql -U customer -d customer -c "select event_type, published_at from outbox order by created_at desc limit 10;"

# Tail a topic
docker compose -f deploy/docker-compose.yml exec kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 \
  --topic customer.events --from-beginning --timeout-ms 5000
```

## Phase 1: origination + decisioning (Docker session)

Services added: `simulators` (8190), `origination` (8102), `decision` (8103), `notification` (8109),
plus the extended `customer` (8101) and the gateway routes `/api/applications`, `/api/decisions`,
`/api/sim` (admin-only).

```bash
# Start everything (core + auth + apps); build images first (jib) as per Phase 0.
docker compose -f deploy/docker-compose.yml --profile core --profile auth --profile apps up -d

# End-to-end personas + failure scenarios through the gateway and simulators:
scripts/e2e/phase1.sh

# Decision latency (informational, needs an admin token):
TOKEN=$(curl -s -X POST http://localhost:8180/realms/lms/protocol/openid-connect/token \
  -d grant_type=password -d client_id=gateway -d client_secret=gateway-secret \
  -d username=admin -d password=admin | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
k6 run -e TOKEN=$TOKEN scripts/k6/phase1-decision-latency.js

# Drive the simulators (personas by phone prefix 9000/9001/9002/9003/9004; presets healthy/slow/flaky/down):
curl -X POST http://localhost:8080/api/sim/config/bureau/preset/down -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/sim/sms/outbox -H "Authorization: Bearer $TOKEN"   # read OTPs
```

Notes: the decision container sets `--enable-preview` (it loads `Fanout`, ADR 0006); the dev velocity
window is in-memory (Redis-Lua is a backlog item); the dev rule set is served from the classpath
(`rule-sets/v1.json`). See `docs/backlog.md` for the remaining Docker-session items.

## Common gotchas

- **JDK 25 required.** System Maven may default to another JDK; always set `JAVA_HOME` to 25. The
  `.mvn/jvm.config` adds the `jdk.compiler` exports google-java-format needs (ADR 0002).
- **Preview features** are compiled only in `lms-concurrency`; its container/test JVMs pass
  `--enable-preview`. Do not add the flag elsewhere.
- **Avro trusted packages.** Services set
  `-Dorg.apache.avro.SERIALIZABLE_PACKAGES=com.lms.contracts` (ADR 0004) via `JAVA_TOOL_OPTIONS`;
  without it Avro deserialization of contract classes fails.
- **No Docker here.** This environment has no container runtime, so ITs/compose/e2e are deferred
  (`docs/backlog.md`). Everything else builds green with `./mvnw clean verify`.
```

## Phase 2: loan core + ledger (Docker session)

```bash
# Start core + auth + apps (build images first via jib, as in Phase 0). loan-account (8104) and
# ledger (8108) are in the `apps` profile; the gateway and loan-account run with SPRING_PROFILES_ACTIVE=dev
# so the business-date and simulate/inject endpoints are active.
docker compose -f deploy/docker-compose.yml --profile core --profile auth --profile apps up -d

# End-to-end (needs an accepted application first — run scripts/e2e/phase1.sh, then):
scripts/e2e/phase2.sh

# Drive a loan by hand (admin token; see phase1 notes for token()):
#   simulate a disbursement (dev):      POST /api/admin/loans/{id}/simulate-disbursement
#   advance the business date (dev):    POST /api/admin/business-date/advance?days=14
#   run interest accrual for a date:    POST /api/admin/jobs/interest-accrual/run?date=YYYY-MM-DD
#   inject a repayment (dev):           POST /api/admin/loans/{id}/inject-repayment {"amountMinor":474000}
#   foreclosure quote:                  GET  /api/loans/{id}/foreclosure-quote?date=YYYY-MM-DD
#   ledger trial balance (zero check):  GET  /api/ledger/trial-balance
#   per-loan sub-ledger:                GET  /api/ledger/loans/{loanId}
#   reverse an entry (admin):           POST /api/journal-entries/{id}/reverse {"reason":"..."}
```

Debugging the loan/ledger path:
- A loan not appearing after acceptance → check the loan-account `application.events` consumer and the
  `inbox_processed` table; the loan is idempotent on `applicationId` (unique).
- A ledger entry missing → the ledger consumes `loan.events`/`disbursement.events`; posting is
  idempotent on `source_event_id`. The trial balance metric is `ledger_trial_balance_diff_minor_units`
  (Prometheus) and must be zero.
- Business date is the Redis key `lms:platform:business-date`; unset = today in Asia/Kolkata.

## Phase 3: money movement (Docker session)

```bash
# disbursement (8105) needs the Temporal dev server, so add the `workflow` profile; repayment (8106)
# is in `apps`. The simulators sign callbacks with LMS_CALLBACK_SECRET (must match the services').
docker compose -f deploy/docker-compose.yml \
  --profile core --profile auth --profile workflow --profile apps up -d

# End-to-end (run phase1.sh/phase2.sh first for an accepted application):
scripts/e2e/phase3.sh

# Drive by hand (admin token):
#   disbursement monitor:            GET  /api/disbursements ; GET /api/disbursements/{id}
#   retry/cancel from MANUAL_REVIEW: POST /api/disbursements/{id}/retry | /cancel
#   register a mandate:              POST /api/mandates {"loanId":"..","customerId":".."}
#   auto-debit job for a date:       POST /api/admin/jobs/auto-debit/run?date=YYYY-MM-DD
#   payment link / manual receipt:   POST /api/repayments | /api/repayments/manual
#   signed provider callbacks:       POST /callbacks/rail | /callbacks/payments | /callbacks/mandates
# Tune the rail/mandate/payments simulators (admin): PUT /api/sim/config/{rail|mandate|payments}
#   knobs: unknownOutcomeRate, outOfOrderRate, returnRate (+ latency/failure/duplicate/delay).
# k6 callback latency (informational): k6 run -e BASE=http://localhost:8080 \
#   -e SECRET=local-dev-callback-secret scripts/k6/phase3-callback-latency.js
```

Debugging the money-movement path:
- A payout stuck → open the disbursement monitor's Temporal-UI link (`disb-{loanId}`); the saga waits
  for the signed `/callbacks/rail` or polls status. A conflicting terminal callback is dropped and
  counted in `lms_disbursement_callback_conflicts` (Prometheus).
- An unsigned/forged callback → the service returns 401 (`CallbackVerifier`); callbacks bypass JWT at
  the gateway but require the HMAC. Duplicates dedup on `(railRef,status)` / `(providerReference,status)`.
- A bounced debit → the mandate callback `RETURNED` publishes `repayment.reversed`; loan-account
  reverses the allocation (new rows, instalments reopened) and ledger reverses the receipt + allocation.
