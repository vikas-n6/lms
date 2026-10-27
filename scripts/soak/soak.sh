#!/usr/bin/env bash
#
# Phase 5 soak test (phase prompt task 6): create SOAK_COUNT loans (default 1000) and run them
# through compressed full lifecycles with a realistic behaviour mix, then run the invariant checker.
# Zero violations is required. Authored for the Docker session; NOT executed here (no Docker) —
# see docs/backlog.md. Reuses the same gateway endpoints as scripts/e2e/phase1.sh/phase2.sh.
#
# Behaviour mix (per loan, deterministic by index): on-time 60%, late-then-cured 20%, bounced 10%,
# foreclosed 5%, defaulted-to-NPA 5%.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="docker compose -f ${ROOT}/deploy/docker-compose.yml"
KC="${KC:-http://localhost:8180}"
GW="${GW:-http://localhost:8080}"
COUNT="${SOAK_COUNT:-1000}"
START_DATE="${SOAK_START_DATE:-2026-01-15}"
TENOR=6

json() { sed -n "s/.*\"$1\":\"\\?\\([^\",}]*\\)\"\\?.*/\\1/p" <<<"$2" | head -1; }
log() { printf '\n=== %s ===\n' "$*"; }
fail() { echo "SOAK FAILED: $*" >&2; exit 1; }
wait_for(){ local u="$1" n="${2:-60}"; for _ in $(seq 1 "$n"); do curl -fsS "$u">/dev/null 2>&1 && return 0; sleep 2; done; fail "timeout $u"; }

api() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-s -o /tmp/soak.out -w '%{http_code}' -X "$method" "${GW}${path}"
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json'
    -H "Idempotency-Key: soak-$(date +%s%N)-$RANDOM")
  [ -n "$body" ] && args+=(-d "$body")
  HTTP=$(curl "${args[@]}"); cat /tmp/soak.out
}
token(){ curl -fsS -X POST "${KC}/realms/lms/protocol/openid-connect/token" -d grant_type=password \
  -d client_id=gateway -d client_secret=gateway-secret -d username="$1" -d password="$1" -d scope=openid \
  | json access_token "$(cat)"; }
otp_from_outbox(){ curl -fsS "${GW}/api/sim/sms/outbox?to=$1" -H "Authorization: Bearer ${TOKEN}" | grep -oE '[0-9]{6}' | tail -1; }
set_business_date(){ api PUT "/api/admin/business-date" "{\"date\":\"$1\"}" >/dev/null || true; }
run_daily(){ api POST "/api/admin/jobs/daily/run?date=$1" >/dev/null || true; }

# Originate -> accept -> disburse one loan for a happy-persona phone (9000 prefix). Echoes loanId.
make_loan() {
  local phone="$1" amount="$2" cust app loan
  cust=$(json id "$(api POST /api/customers "{\"fullName\":\"Soak\",\"phone\":\"${phone}\",\"dateOfBirth\":\"1992-01-01\"}")")
  api POST "/api/customers/${cust}/otp/send" "{\"phone\":\"${phone}\"}" >/dev/null
  api POST "/api/customers/${cust}/otp/verify" "{\"code\":\"$(otp_from_outbox "$phone")\"}" >/dev/null
  api POST "/api/customers/${cust}/kyc" '{"idNumber":"ABCDE1234F"}' >/dev/null
  api POST "/api/customers/${cust}/bank-accounts" '{"accountNumber":"123456789012","ifsc":"HDFC0001"}' >/dev/null
  for c in DATA_PROCESSING CREDIT_PULL LOAN_AGREEMENT; do
    api POST "/api/customers/${cust}/consents" "{\"type\":\"${c}\"}" >/dev/null
  done
  app=$(json id "$(api POST /api/applications \
    "{\"customerId\":\"${cust}\",\"productId\":\"MICRO-V1\",\"requestedAmountMinor\":${amount},\"phone\":\"${phone}\",\"dateOfBirth\":\"1992-01-01\",\"declaredMonthlyIncomeMinor\":5000000,\"employmentType\":\"SALARIED\",\"dataProcessingConsent\":true,\"creditPullConsent\":true,\"loanAgreementConsent\":true}")")
  api POST "/api/applications/${app}/submit" >/dev/null
  sleep 0.2
  api POST "/api/applications/${app}/esign" >/dev/null
  api POST "/api/applications/${app}/accept" "{\"tenorMonths\":${TENOR},\"otpCode\":\"$(otp_from_outbox "$phone")\"}" >/dev/null
  # The loan is created from application.accepted; disburse it.
  loan=$(json id "$(api GET "/api/loans?applicationId=${app}")")
  [ -n "$loan" ] && api POST "/api/admin/loans/${loan}/simulate-disbursement" >/dev/null
  echo "$loan"
}

pay_emi() { # pay the current EMI on a loan (manual receipt path)
  local loan="$1"
  local emi; emi=$(json emiMinor "$(api GET "/api/loans/${loan}")")
  api POST "/api/repayments/manual" \
    "{\"loanId\":\"${loan}\",\"amountMinor\":${emi:-0},\"currency\":\"INR\",\"bankReference\":\"SOAK-$RANDOM\"}" >/dev/null
}

behaviour_for() { # deterministic bucket by index
  local i=$1 m=$((i % 20))
  if   [ $m -lt 12 ]; then echo on-time
  elif [ $m -lt 16 ]; then echo late
  elif [ $m -lt 18 ]; then echo bounced
  elif [ $m -lt 19 ]; then echo foreclosed
  else echo defaulted; fi
}

wait_for "${GW}/api/status" 120
TOKEN=$(token admin) || fail "no admin token"
api POST "/api/sim/reset" >/dev/null || true
set_business_date "$START_DATE"

log "Originating ${COUNT} loans"
declare -a LOANS BEHAVIOURS
for i in $(seq 1 "$COUNT"); do
  phone="9000$(printf '%06d' "$i")"
  amount=$(( (RANDOM % 91 + 10) * 50000 ))   # 5,000.00 .. 50,000.00 in 500.00 steps
  loan=$(make_loan "$phone" "$amount")
  LOANS[$i]="$loan"; BEHAVIOURS[$i]="$(behaviour_for "$i")"
  [ $((i % 50)) -eq 0 ] && echo "  originated $i/${COUNT}"
done

log "Running ${TENOR} monthly cycles with the behaviour mix"
for cycle in $(seq 1 "$TENOR"); do
  due_date=$(date -u -d "${START_DATE} +${cycle} month" +%F 2>/dev/null || date -u -v+"${cycle}"m -j -f %F "${START_DATE}" +%F)
  set_business_date "$due_date"; run_daily "$due_date"
  for i in $(seq 1 "$COUNT"); do
    loan="${LOANS[$i]}"; [ -z "$loan" ] && continue
    case "${BEHAVIOURS[$i]}" in
      on-time)   pay_emi "$loan" ;;
      late)      [ $((cycle % 2)) -eq 0 ] && pay_emi "$loan" ;;    # pays every other month, then cures
      bounced)   [ "$cycle" -ge 2 ] && pay_emi "$loan" ;;          # first debit bounces, then pays
      foreclosed)
        if [ "$cycle" -eq 3 ]; then
          q=$(json amount "$(api GET "/api/loans/${loan}/foreclosure-quote")")
          api POST "/api/repayments/manual" "{\"loanId\":\"${loan}\",\"amountMinor\":${q:-0},\"currency\":\"INR\",\"bankReference\":\"SOAK-FC-$i\",\"purpose\":\"FORECLOSURE\",\"foreclosureQuoteId\":\"soak-$i\"}" >/dev/null
        fi ;;
      defaulted) : ;;                                              # never pays -> rolls to NPA
    esac
  done
  # advance a few extra days so DPD ticks fire for the non-payers
  for d in 3 10 20; do
    tick=$(date -u -d "${due_date} +${d} day" +%F 2>/dev/null || date -u -v+"${d}"d -j -f %F "${due_date}" +%F)
    set_business_date "$tick"; run_daily "$tick"
  done
done

log "Invariant check"
bash "${ROOT}/scripts/verify-invariants.sh"
echo
echo "SOAK: ${COUNT} loans completed"
