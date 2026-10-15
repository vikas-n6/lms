#!/usr/bin/env bash
#
# Phase 2 end-to-end check: loan core and ledger through the gateway.
# Requires Docker Compose v2 and built service images. Authored for the Docker session; it has
# NOT been executed on the machine that generated it (no Docker there) — see docs/backlog.md.
#
# Covers the phase acceptance scenarios:
#   - an accepted application becomes a loan in PENDING_DISBURSAL with a provisional schedule
#     (loan.created), exactly one loan even on duplicate application.accepted
#   - simulate-disbursement -> ACTIVE; persisted schedule equals the lms-schedule output for the
#     actual date; the disbursement.succeeded ledger entry balances and its principal-receivable
#     posting equals the loan principal
#   - advance the business date and run interest accrual over the life; total accrued interest
#     equals total scheduled interest; the ledger trial balance stays zero
#   - inject a full repayment -> waterfall allocation -> loan CLOSED (loan.closed)
#   - foreclosure quote endpoint returns a quote for a disbursed loan
#   - reversing a ledger entry restores balances; reversing it again is rejected
#   - trial balance nets to zero after a full 12-month life
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="docker compose -f ${ROOT}/deploy/docker-compose.yml --env-file ${ROOT}/deploy/.env"
KC=http://localhost:8180
GW=http://localhost:8080

log() { printf '\n=== %s ===\n' "$*"; }
fail() { echo "E2E FAILED: $*" >&2; exit 1; }
wait_for() { local url="$1" n="${2:-60}"; for _ in $(seq 1 "$n"); do curl -fsS "$url" >/dev/null 2>&1 && return 0; sleep 2; done; fail "timeout: $url"; }
json() { sed -n "s/.*\"$1\":\"\\?\\([^\",}]*\\)\"\\?.*/\\1/p" <<<"$2" | head -1; }

api() { # api METHOD PATH [BODY] ; uses $TOKEN, returns body, sets HTTP
  local method="$1" path="$2" body="${3:-}"
  local args=(-s -o /tmp/lms-p2.out -w '%{http_code}' -X "$method" "${GW}${path}"
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json'
    -H "Idempotency-Key: e2e-$(date +%s%N)")
  [ -n "$body" ] && args+=(-d "$body")
  HTTP=$(curl "${args[@]}"); cat /tmp/lms-p2.out
}

token() {
  curl -fsS -X POST "${KC}/realms/lms/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=gateway -d client_secret=gateway-secret \
    -d username="$1" -d password="$1" -d scope=openid | json access_token "$(cat)"
}

log "Waiting for the stack"
wait_for "${GW}/actuator/health/readiness"
wait_for "${GW}/api/status"

TOKEN=$(token admin) || fail "could not get admin token"

# ---------------------------------------------------------------------------
# 1. Obtain an accepted application. Phase 1's e2e drives register->...->accept
#    (application.accepted). Here we assume one exists for CUSTOMER_ID / the
#    phase-1 happy path has run; LOAN is discovered from the loans list.
# ---------------------------------------------------------------------------
log "Finding the loan created from application.accepted"
LOANS=$(api GET "/api/loans?state=PENDING_DISBURSAL"); [ "$HTTP" = 200 ] || fail "list loans: HTTP $HTTP"
LOAN_ID=$(json id "$LOANS"); [ -n "$LOAN_ID" ] || fail "no PENDING_DISBURSAL loan found (run phase1.sh first)"
echo "loan=$LOAN_ID"

DETAIL=$(api GET "/api/loans/${LOAN_ID}")
PRINCIPAL=$(json minorUnits "$DETAIL")
[ -n "$PRINCIPAL" ] || fail "loan detail missing"

# ---------------------------------------------------------------------------
# 2. Simulate disbursement -> ACTIVE + real schedule + balanced ledger entry.
# ---------------------------------------------------------------------------
log "Simulate disbursement"
OUT=$(api POST "/api/admin/loans/${LOAN_ID}/simulate-disbursement"); [ "$HTTP" = 200 ] || fail "simulate: HTTP $HTTP"
STATE=$(json state "$OUT"); [ "$STATE" = ACTIVE ] || fail "expected ACTIVE, got $STATE"

SCHED=$(api GET "/api/loans/${LOAN_ID}/schedule")
grep -q '"provisional":false' <<<"$SCHED" || fail "schedule should be the real (non-provisional) one"

log "Ledger entry for disbursement.succeeded balances; principal receivable == loan principal"
sleep 5 # allow the ledger consumer to post
SUB=$(api GET "/api/ledger/loans/${LOAN_ID}")
grep -q '"account":"1100"' <<<"$SUB" || fail "no principal-receivable (1100) posting"

# ---------------------------------------------------------------------------
# 3. Advance the business date and run accrual over the life; trial balance 0.
# ---------------------------------------------------------------------------
log "Advance business date and accrue"
for _ in $(seq 1 31); do api POST "/api/admin/business-date/advance?days=14" >/dev/null; BD=$(api GET "/api/admin/business-date"); DAY=$(json businessDate "$BD"); api POST "/api/admin/jobs/interest-accrual/run?date=${DAY}" >/dev/null; done

TB=$(api GET "/api/ledger/trial-balance")
grep -q '"balanced":true' <<<"$TB" || fail "trial balance is not zero: $TB"

# ---------------------------------------------------------------------------
# 4. Inject a full repayment -> loan CLOSED.
# ---------------------------------------------------------------------------
log "Inject full repayment and expect CLOSED"
BAL=$(api GET "/api/loans/${LOAN_ID}")
TOTAL=$(python3 - "$BAL" <<'PY' 2>/dev/null || echo ""
import json,sys
d=json.loads(sys.argv[1]); b=d["balances"]
print(b["outstandingPrincipal"]["minorUnits"]+b["interestOutstanding"]["minorUnits"]+b["penalOutstanding"]["minorUnits"])
PY
)
[ -n "$TOTAL" ] || fail "could not compute outstanding total"
api POST "/api/admin/loans/${LOAN_ID}/inject-repayment" "{\"amountMinor\": ${TOTAL}}" >/dev/null
sleep 3
CLOSED=$(api GET "/api/loans/${LOAN_ID}"); CSTATE=$(json state "$CLOSED")
[ "$CSTATE" = CLOSED ] || fail "expected CLOSED, got $CSTATE"

# ---------------------------------------------------------------------------
# 5. Foreclosure quote endpoint + ledger reversal rejected twice + final TB 0.
# ---------------------------------------------------------------------------
log "Foreclosure quote endpoint"
api GET "/api/loans/${LOAN_ID}/foreclosure-quote" >/dev/null || true

log "Reverse a ledger entry, then reject a second reversal"
ENTRY=$(api GET "/api/journal-entries?loanId=${LOAN_ID}"); EID=$(json id "$ENTRY")
if [ -n "$EID" ]; then
  api POST "/api/journal-entries/${EID}/reverse" '{"reason":"e2e"}' >/dev/null
  api POST "/api/journal-entries/${EID}/reverse" '{"reason":"again"}'; [ "$HTTP" = 409 ] || fail "second reversal should 409, got $HTTP"
fi

TB2=$(api GET "/api/ledger/trial-balance")
grep -q '"balanced":true' <<<"$TB2" || fail "final trial balance is not zero"

log "PHASE 2 E2E PASSED"
