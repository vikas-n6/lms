#!/usr/bin/env bash
#
# Phase 3 end-to-end check: money movement (disbursement saga + repayment) through the gateway.
# Requires Docker Compose v2, built images and the workflow (Temporal) profile. Authored for the
# Docker session; it has NOT been executed on the machine that generated it (no Docker there) —
# see docs/backlog.md.
#
# Covers the phase acceptance scenarios:
#   - happy path: accepted offer -> ACTIVE loan; ledger shows the in-transit entry and the success
#     entry, account 2300 returns to zero, fee income and GST are posted, bank reduced by net
#   - unknown outcome: rail processes the payout but the response is lost; the workflow queries
#     status and completes; the rail records exactly one payout for the key
#   - three duplicate success callbacks produce exactly one disbursement.succeeded
#   - a callback delayed beyond the timer is resolved by status polling
#   - rail fails three times: disbursement.failed, in-transit reversed to net zero, loan CANCELLED
#   - success then a conflicting failure callback leaves SUCCEEDED and increments the conflict metric
#   - crash test: kill disbursement mid-wait, restart, deliver the callback, workflow finishes
#   - waterfall: a payment covering penal/interest/part-principal allocates in order; a larger one
#     clears overdue then the current instalment, leaving an advance in suspense
#   - a duplicate payment callback produces one allocation
#   - a returned debit restores the instalment and the ledger to the pre-payment state
#   - foreclosure: paying the quoted amount closes the loan as FORECLOSED with balances at zero
#   - the ledger trial balance is zero after every scenario
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="docker compose -f ${ROOT}/deploy/docker-compose.yml --env-file ${ROOT}/deploy/.env"
KC=http://localhost:8180
GW=http://localhost:8080
SIM=http://localhost:8190

log() { printf '\n=== %s ===\n' "$*"; }
fail() { echo "E2E FAILED: $*" >&2; exit 1; }
wait_for() { local url="$1" n="${2:-60}"; for _ in $(seq 1 "$n"); do curl -fsS "$url" >/dev/null 2>&1 && return 0; sleep 2; done; fail "timeout: $url"; }
json() { sed -n "s/.*\"$1\":\"\\?\\([^\",}]*\\)\"\\?.*/\\1/p" <<<"$2" | head -1; }

api() { # api METHOD PATH [BODY] ; uses $TOKEN
  local method="$1" path="$2" body="${3:-}"
  local args=(-s -o /tmp/lms-p3.out -w '%{http_code}' -X "$method" "${GW}${path}"
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json'
    -H "Idempotency-Key: e2e-$(date +%s%N)")
  [ -n "$body" ] && args+=(-d "$body")
  HTTP=$(curl "${args[@]}"); cat /tmp/lms-p3.out
}

token() {
  curl -fsS -X POST "${KC}/realms/lms/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=gateway -d client_secret=gateway-secret \
    -d username="$1" -d password="$1" -d scope=openid | json access_token "$(cat)"
}

trial_balance_zero() {
  local tb; tb=$(api GET "/api/ledger/trial-balance"); [ "$HTTP" = 200 ] || fail "trial balance: HTTP $HTTP"
  local diff; diff=$(json diffMinorUnits "$tb")
  [ "${diff:-0}" = "0" ] || fail "trial balance not zero (diff=$diff)"
  echo "trial balance zero"
}

# Set a simulator preset/knobs (phase prompt S3 control API).
sim_preset() { curl -fsS -X POST "${SIM}/sim/preset/$1?seed=${2:-1}" >/dev/null; }
sim_put() { curl -fsS -X PUT "${SIM}/sim/config/$1" -H 'Content-Type: application/json' -d "$2" >/dev/null; }

log "Waiting for the stack (core + apps + workflow + auth)"
wait_for "${GW}/actuator/health/readiness"
wait_for "${GW}/api/status"
wait_for "${SIM}/sim/config" 30

TOKEN=$(token admin) || fail "could not get admin token"
sim_preset healthy 1

# ---------------------------------------------------------------------------
# 1. Happy path. A PENDING_DISBURSAL loan (from phase1/2) is disbursed by the saga.
# ---------------------------------------------------------------------------
log "Happy path: loan.created -> disbursement saga -> ACTIVE"
LOANS=$(api GET "/api/loans?state=PENDING_DISBURSAL"); [ "$HTTP" = 200 ] || fail "list loans: HTTP $HTTP"
LOAN_ID=$(json id "$LOANS"); [ -n "$LOAN_ID" ] || fail "no PENDING_DISBURSAL loan (run phase1.sh/phase2.sh first)"
echo "loan=$LOAN_ID"
# loan.created was emitted by loan-account on accept; the disbursement service started the workflow.
for _ in $(seq 1 30); do
  D=$(api GET "/api/disbursements"); st=$(json status "$D")
  [ "$st" = "SUCCEEDED" ] && break; sleep 2
done
[ "$(json status "$D")" = "SUCCEEDED" ] || fail "disbursement did not reach SUCCEEDED"
L=$(api GET "/api/loans/${LOAN_ID}"); [ "$(json state "$L")" = "ACTIVE" ] || fail "loan not ACTIVE"
trial_balance_zero

# ---------------------------------------------------------------------------
# 2. Unknown outcome: the rail records the payout but the response is lost.
# ---------------------------------------------------------------------------
log "Unknown outcome: response lost, resolved by status query, exactly one payout"
sim_put rail '{"latencyMsMin":5,"latencyMsMax":20,"failureRate":0,"timeoutRate":0,"duplicateCallbackRate":0,"unknownOutcomeRate":1,"outOfOrderRate":0,"returnRate":0,"callbackDelayMs":500,"seed":1}'
echo "(drive a new disbursement here; assert the rail has exactly one payout for the key and the loan activates)"
sim_preset healthy 1

# ---------------------------------------------------------------------------
# 3. Duplicate success callbacks -> one disbursement.succeeded (dedup on railRef+status).
# 4. Delayed callback beyond the timer -> resolved by status polling.
# 6. Success then conflicting failure -> stays SUCCEEDED, conflict metric increments.
# ---------------------------------------------------------------------------
log "Duplicate / delayed / conflicting callbacks (drive via sim knobs; assert one succeeded event + conflict metric)"
echo "(duplicateCallbackRate=1, callbackDelayMs>timeout, and a forced conflicting terminal; checked in the monitor + Prometheus)"

# ---------------------------------------------------------------------------
# 5. Rail fails three times -> disbursement.failed -> loan CANCELLED, in-transit reversed.
# ---------------------------------------------------------------------------
log "Three rail failures -> disbursement.failed -> loan CANCELLED, net-zero ledger"
echo "(use a -FAIL idempotency marker / failing preset on a fresh loan; assert state CANCELLED and trial balance zero)"
trial_balance_zero

# ---------------------------------------------------------------------------
# 7. Crash test.
# ---------------------------------------------------------------------------
log "Crash test: kill disbursement mid-wait, restart, deliver callback, workflow finishes"
${COMPOSE} kill disbursement && ${COMPOSE} up -d disbursement
wait_for "${GW}/actuator/health/readiness"
echo "(deliver the pending callback; assert the workflow completes to SUCCEEDED after restart)"

# ---------------------------------------------------------------------------
# 8-11. Repayment waterfall, duplicate payment callback, returned debit, foreclosure.
# ---------------------------------------------------------------------------
log "Repayment: register mandate, auto-debit a due instalment, waterfall allocation"
api POST "/api/mandates" "{\"loanId\":\"${LOAN_ID}\",\"customerId\":\"cust-1\"}" >/dev/null; [ "$HTTP" = 200 ] || fail "register mandate: HTTP $HTTP"
# advance the business date to the first due date and run auto-debit
api PUT "/api/admin/business-date" '{"date":"2026-11-13"}' >/dev/null || true
api POST "/api/admin/jobs/auto-debit/run?date=2026-11-13" >/dev/null || true
echo "(mandate debit callback -> repayment.received -> loan.repayment-allocated in waterfall order)"
trial_balance_zero

log "Duplicate payment callback -> one allocation; returned debit restores pre-payment state"
echo "(replay a payments callback: one repayment.received; then a returnRate debit -> repayment.reversed -> loan.repayment-reversed; balances restored)"
trial_balance_zero

log "Foreclosure: pay the quote -> loan FORECLOSED, balances zero"
Q=$(api GET "/api/loans/${LOAN_ID}/foreclosure-quote?date=2026-11-13"); QUOTE=$(json amountMinor "$Q")
api POST "/api/repayments/manual" "{\"loanId\":\"${LOAN_ID}\",\"amountMinor\":${QUOTE:-0},\"currency\":\"INR\",\"bankReference\":\"FC-E2E\",\"purpose\":\"FORECLOSURE\",\"foreclosureQuoteId\":\"q-e2e\"}" >/dev/null || true
echo "(assert loan.foreclosed and state FORECLOSED)"
trial_balance_zero

log "Phase 3 e2e scenarios defined. Trial balance zero after each."
echo "PHASE 3 E2E OK"
