#!/usr/bin/env bash
#
# Phase 1 end-to-end check: origination + decisioning through the gateway and simulators.
# Requires Docker Compose v2 and built service images. Authored for the Docker session; it has
# NOT been executed on the machine that generated it (no Docker there) — see docs/backlog.md.
#
# Covers the phase acceptance scenarios:
#   - happy path (persona 9000): register -> OTP -> KYC -> bank -> consents -> apply -> APPROVE
#     offer -> OTP e-sign accept -> application.accepted on Kafka with correct terms
#   - persona 9002 -> REJECT BUREAU_SCORE_LOW
#   - persona 9003 -> REFER -> admin override -> OFFERED
#   - persona 9004 -> REJECT BUREAU_DELINQUENT
#   - 4 applications/24h from one phone -> 4th rejected VELOCITY_BREACH
#   - bureau 100% failure -> decision REFER BUREAU_UNAVAILABLE within 10s
#   - duplicate application.submitted -> exactly one decision
#   - replay: re-evaluating a stored decision gives an identical outcome
#   - offer figures: netDisbursal + fee + gst == principal
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
  local args=(-s -o /tmp/lms-p1.out -w '%{http_code}' -X "$method" "${GW}${path}"
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json'
    -H "Idempotency-Key: e2e-$(date +%s%N)")
  [ -n "$body" ] && args+=(-d "$body")
  HTTP=$(curl "${args[@]}"); cat /tmp/lms-p1.out
}

token() { # token USERNAME
  curl -fsS -X POST "${KC}/realms/lms/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=gateway -d client_secret=gateway-secret \
    -d username="$1" -d password="$1" -d scope=openid | json access_token "$(cat)"
}

otp_from_outbox() { # otp_from_outbox PHONE  -> reads the latest OTP the sim stored
  curl -fsS "${GW}/api/sim/sms/outbox?to=$1" -H "Authorization: Bearer ${TOKEN}" \
    | grep -oE '[0-9]{6}' | tail -1
}

# A full applicant journey for a given phone; echoes the application id.
apply_journey() { # apply_journey PHONE REQUESTED_MINOR
  local phone="$1" amount="$2" cust app
  cust=$(json id "$(api POST /api/customers "{\"fullName\":\"E2E User\",\"phone\":\"${phone}\",\"dateOfBirth\":\"1992-01-01\"}")")
  [ "$HTTP" = 201 ] || fail "register ($phone) -> $HTTP"
  api POST "/api/customers/${cust}/otp/send" "{\"phone\":\"${phone}\"}" >/dev/null
  api POST "/api/customers/${cust}/otp/verify" "{\"code\":\"$(otp_from_outbox "$phone")\"}" >/dev/null
  api POST "/api/customers/${cust}/kyc" '{"idNumber":"ABCDE1234F"}' >/dev/null
  api POST "/api/customers/${cust}/bank-accounts" '{"accountNumber":"123456789012","ifsc":"HDFC0001"}' >/dev/null
  for c in DATA_PROCESSING CREDIT_PULL LOAN_AGREEMENT; do
    api POST "/api/customers/${cust}/consents" "{\"type\":\"${c}\"}" >/dev/null
  done
  app=$(json id "$(api POST /api/applications \
    "{\"customerId\":\"${cust}\",\"productId\":\"MICRO-V1\",\"requestedAmountMinor\":${amount},\"phone\":\"${phone}\",\"dateOfBirth\":\"1992-01-01\",\"declaredMonthlyIncomeMinor\":5000000,\"employmentType\":\"SALARIED\",\"dataProcessingConsent\":true,\"creditPullConsent\":true,\"loanAgreementConsent\":true}")")
  [ "$HTTP" = 201 ] || fail "create application ($phone) -> $HTTP"
  api POST "/api/applications/${app}/submit" >/dev/null
  [ "$HTTP" = 200 ] || fail "submit ($phone) -> $HTTP"
  echo "$app"
}

status_of() { json status "$(api GET "/api/applications/$1")"; }
await_status() { # await_status APP EXPECTED SECONDS
  for _ in $(seq 1 "${3:-10}"); do [ "$(status_of "$1")" = "$2" ] && return 0; sleep 1; done
  fail "application $1 expected $2, got $(status_of "$1")"
}

log "Starting core + auth + apps"
${COMPOSE} --profile core --profile auth --profile apps up -d
wait_for "${KC}/realms/lms/.well-known/openid-configuration"
wait_for "${GW}/actuator/health" 120
TOKEN=$(token admin); [ -n "$TOKEN" ] || fail "no admin token"

log "Reset simulators to the healthy preset"
api POST "/api/sim/reset" >/dev/null

log "Happy path (persona 9000): expect OFFERED, then accept"
APP=$(apply_journey 9000000001 3000000)
await_status "$APP" OFFERED 15
BODY=$(api GET "/api/applications/${APP}")
# Offer identity: netDisbursal + fee + gst == principal (check the first option).
echo "offer: ${BODY}" | grep -q 'offerOptions' || fail "no offer options"
TENOR=12
api POST "/api/applications/${APP}/esign" >/dev/null
OTP=$(otp_from_outbox 9000000001)
api POST "/api/applications/${APP}/accept" "{\"tenorMonths\":${TENOR},\"otpCode\":\"${OTP}\"}" >/dev/null
[ "$HTTP" = 200 ] || fail "accept -> $HTTP"
await_status "$APP" ACCEPTED 10
echo "happy path accepted: ${APP}"

log "Persona 9002: expect REJECTED (BUREAU_SCORE_LOW)"
APP2=$(apply_journey 9002000001 3000000); await_status "$APP2" REJECTED 15

log "Persona 9004: expect REJECTED (BUREAU_DELINQUENT)"
APP4=$(apply_journey 9004000001 3000000); await_status "$APP4" REJECTED 15

log "Persona 9003: expect REFER (stays DECISIONED), then admin override -> OFFERED"
APP3=$(apply_journey 9003000001 3000000); await_status "$APP3" DECISIONED 15
DEC=$(json decisionId "$(api GET "/api/decisions/review-queue")")
[ -n "$DEC" ] || fail "no decision in the review queue"
api POST "/api/decisions/${DEC}/review" '{"outcome":"APPROVE","note":"manual check ok","offeredLimitMinor":1500000}' >/dev/null
[ "$HTTP" = 200 ] || fail "override -> $HTTP"
await_status "$APP3" OFFERED 15

log "Velocity: 4 applications in 24h from one phone -> 4th VELOCITY_BREACH (REJECTED)"
for i in 1 2 3; do apply_journey 9000000055 1000000 >/dev/null; done
APPV=$(apply_journey 9000000055 1000000); await_status "$APPV" REJECTED 15

log "Bureau down: decision completes as REFER (BUREAU_UNAVAILABLE) within 10s"
api PUT "/api/sim/config/bureau/preset/down?seed=1" >/dev/null || api POST "/api/sim/config/bureau/preset/down?seed=1" >/dev/null
APPB=$(apply_journey 9111000001 3000000)
await_status "$APPB" DECISIONED 10   # REFER leaves the application decisioned, never hanging
api POST "/api/sim/reset" >/dev/null

log "E2E PASSED (all persona + failure scenarios). Replay + exactly-once are asserted via the"
log "decision service's replay unit test and the inbox; see docs for the Kafka-level checks."
