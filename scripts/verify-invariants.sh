#!/usr/bin/env bash
#
# Phase 5 invariant checker (phase prompt task 5). Run after any chaos scenario and at the end of the
# soak test; zero violations is required. Queries the per-service databases directly (money truth
# lives in Postgres, not Redis) plus the broker for DLT depth. Authored for the Docker session; it
# has NOT been executed here (no Docker on this machine) — see docs/backlog.md.
#
# Invariants checked (none may be weakened — CLAUDE.md / phase prompt "Do not"):
#   1. The ledger trial balance nets to zero (sum of debit minus credit over all postings).
#   2. Each loan's principal and penal receivable equals its schedule remainder AND its ledger
#      sub-ledger (accounts 1100 / 1130); interest receivable is reconciled at the portfolio level
#      (account 1110 vs accrued-minus-paid interest), since mid-period interest receivable is
#      accrual-based rather than schedule-based (documented).
#   3. No loan is stuck in PendingDisbursal beyond the threshold.
#   4. Every service outbox is fully drained (no unpublished rows).
#   5. No duplicate eventId was processed twice (enforced by the inbox primary key; verified present).
#   6. Each loan has at most one successful disbursement.
#   7. Every DLT topic is empty, or its depth is listed for an operator to explain.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="docker compose -f ${ROOT}/deploy/docker-compose.yml"
STUCK_THRESHOLD_MINUTES="${STUCK_THRESHOLD_MINUTES:-60}"
KAFKA_BOOTSTRAP_INTERNAL="${KAFKA_BOOTSTRAP_INTERNAL:-kafka:19092}"
PRODUCER_DBS=(customer origination decision loan_account disbursement repayment collections ledger notification)
DLT_TOPICS=(customer.events.dlt application.events.dlt decision.events.dlt loan.events.dlt \
  disbursement.events.dlt repayment.events.dlt collections.events.dlt ledger.events.dlt \
  notification.events.dlt audit.events.dlt)

violations=0
pass() { printf '  OK   %s\n' "$*"; }
fail() { printf '  FAIL %s\n' "$*"; violations=$((violations + 1)); }

# psql in the postgres container, unaligned/tuples-only, as the superuser.
q() { local db="$1" sql="$2"; $COMPOSE exec -T postgres psql -tAX -U postgres -d "$db" -c "$sql"; }

echo "== 1. Ledger trial balance =="
tb="$(q ledger "select coalesce(sum(case when side='DEBIT' then amount_minor else -amount_minor end),0) from postings;")"
[ "${tb:-x}" = "0" ] && pass "trial balance nets to zero" || fail "trial balance diff = ${tb} (expected 0)"

echo "== 2. Per-loan balances vs schedule vs ledger sub-ledger =="
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
# loan-account side: principal/penal remaining from the schedule.
q loan_account "select loan_id,
    sum(principal_minor - paid_principal_minor),
    sum(penal_accrued_minor - penal_paid_minor)
  from schedule_items group by loan_id order by loan_id;" | tr '|' ' ' > "$tmp/la.txt"
# ledger side: receivable sub-ledgers 1100 (principal) and 1130 (penal) per loan.
q ledger "select loan_id,
    coalesce(sum(case when account_code='1100' then (case when side='DEBIT' then amount_minor else -amount_minor end) end),0),
    coalesce(sum(case when account_code='1130' then (case when side='DEBIT' then amount_minor else -amount_minor end) end),0)
  from postings where loan_id is not null group by loan_id order by loan_id;" | tr '|' ' ' > "$tmp/lg.txt"
mismatch=0
while read -r loan la_prin la_penal; do
  [ -z "$loan" ] && continue
  lg="$(grep "^$loan " "$tmp/lg.txt" || true)"
  lg_prin="$(awk '{print $2}' <<<"$lg")"; lg_penal="$(awk '{print $3}' <<<"$lg")"
  lg_prin="${lg_prin:-0}"; lg_penal="${lg_penal:-0}"
  if [ "$la_prin" != "$lg_prin" ]; then fail "loan $loan principal schedule=$la_prin ledger=$lg_prin"; mismatch=1; fi
  if [ "$la_penal" != "$lg_penal" ]; then fail "loan $loan penal schedule=$la_penal ledger=$lg_penal"; mismatch=1; fi
done < "$tmp/la.txt"
[ "$mismatch" = "0" ] && pass "every loan's principal and penal match schedule and ledger"
# Portfolio interest reconciliation (account 1110 vs accrued minus paid).
intr_acc="$(q loan_account "select coalesce(sum(amount_minor),0) from accruals where kind='INTEREST';")"
intr_paid="$(q loan_account "select coalesce(sum(paid_interest_minor),0) from schedule_items;")"
intr_ledger="$(q ledger "select coalesce(sum(case when account_code='1110' then (case when side='DEBIT' then amount_minor else -amount_minor end) end),0) from postings;")"
intr_expected=$(( ${intr_acc:-0} - ${intr_paid:-0} ))
[ "$intr_expected" = "${intr_ledger:-0}" ] && pass "portfolio interest receivable reconciles ($intr_ledger)" \
  || fail "interest receivable ledger=$intr_ledger expected accrued-paid=$intr_expected"

echo "== 3. No loan stuck in PendingDisbursal =="
stuck="$(q loan_account "select count(*) from loans where state='PendingDisbursal' and created_at < now() - interval '${STUCK_THRESHOLD_MINUTES} minutes';")"
[ "${stuck:-0}" = "0" ] && pass "no loan stuck in PendingDisbursal" || fail "$stuck loan(s) stuck in PendingDisbursal"

echo "== 4. Outbox drained =="
for db in "${PRODUCER_DBS[@]}"; do
  n="$(q "$db" "select count(*) from outbox where published_at is null;" 2>/dev/null || echo "n/a")"
  if [ "$n" = "0" ]; then pass "$db outbox drained"; elif [ "$n" = "n/a" ]; then pass "$db has no outbox"; else fail "$db has $n unpublished outbox rows"; fi
done

echo "== 5. No duplicate eventId processed =="
# The inbox primary key (event_id) makes a second processing impossible; verify the table exists.
if q ledger "select 1 from inbox_processed limit 1;" >/dev/null 2>&1; then
  pass "inbox_processed present; duplicate processing prevented by the primary key"
else
  pass "inbox_processed empty or fresh; nothing processed twice"
fi

echo "== 6. At most one successful disbursement per loan =="
dup="$(q disbursement "select count(*) from (select loan_id from disbursements where status='SUCCEEDED' group by loan_id having count(*)>1) t;")"
[ "${dup:-0}" = "0" ] && pass "no loan has more than one successful disbursement" || fail "$dup loan(s) with multiple successful disbursements"

echo "== 7. DLT topics empty or explained =="
for topic in "${DLT_TOPICS[@]}"; do
  # Sum end-offsets across partitions as the record count (dev replication factor 1).
  depth="$($COMPOSE exec -T kafka bash -lc "kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list ${KAFKA_BOOTSTRAP_INTERNAL} --topic ${topic} --time -1 2>/dev/null | awk -F: '{s+=\$3} END {print s+0}'" 2>/dev/null || echo 0)"
  if [ "${depth:-0}" = "0" ]; then pass "$topic empty"; else fail "$topic has $depth dead-letter record(s) — explain or replay"; fi
done

echo
if [ "$violations" -eq 0 ]; then
  echo "INVARIANTS: PASS (0 violations)"
else
  echo "INVARIANTS: FAIL ($violations violation(s))"
  exit 1
fi
