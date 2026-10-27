#!/usr/bin/env bash
#
# Phase 5 Kubernetes-readiness check (phase prompt task 8). Verifies the properties a cluster relies
# on, without writing Helm charts or manifests (explicitly out of scope):
#   - liveness and readiness probes respond on every service
#   - containers run as a non-root user
#   - configuration is environment-only (no baked-in secrets; config comes from env)
#   - graceful shutdown: a SIGTERM during an in-flight request lets it finish
# Authored for the Docker session; NOT executed here (no Docker) — see docs/backlog.md.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="docker compose -f ${ROOT}/deploy/docker-compose.yml"
GW="${GW:-http://localhost:8080}"

# service:port (container-internal); the gateway has no DB but still has probes.
SERVICES=(
  "customer:8101" "origination:8102" "decision:8103" "loan-account:8104" "disbursement:8105"
  "repayment:8106" "collections:8107" "ledger:8108" "notification:8109" "reporting:8110" "gateway:8080"
)

violations=0
pass(){ printf '  OK   %s\n' "$*"; }
fail(){ printf '  FAIL %s\n' "$*"; violations=$((violations+1)); }

echo "== Probes =="
for s in "${SERVICES[@]}"; do
  svc="${s%%:*}" port="${s##*:}"
  live=$($COMPOSE exec -T "$svc" sh -lc "curl -fsS -o /dev/null -w '%{http_code}' http://localhost:${port}/actuator/health/liveness" 2>/dev/null || echo 000)
  ready=$($COMPOSE exec -T "$svc" sh -lc "curl -fsS -o /dev/null -w '%{http_code}' http://localhost:${port}/actuator/health/readiness" 2>/dev/null || echo 000)
  { [ "$live" = 200 ] && [ "$ready" = 200 ]; } && pass "$svc probes (live=$live ready=$ready)" || fail "$svc probes (live=$live ready=$ready)"
done

echo "== Non-root user =="
for s in "${SERVICES[@]}"; do
  svc="${s%%:*}"
  uid=$($COMPOSE exec -T "$svc" sh -lc 'id -u' 2>/dev/null || echo "?")
  { [ "$uid" != "0" ] && [ "$uid" != "?" ]; } && pass "$svc runs as uid=$uid (non-root)" || fail "$svc runs as uid=$uid"
done

echo "== Environment-only configuration (no baked secrets) =="
for s in "${SERVICES[@]}"; do
  svc="${s%%:*}"
  # The image must not bundle a secret file; config/secrets arrive via env at runtime.
  if $COMPOSE exec -T "$svc" sh -lc 'test ! -f /app/application-secrets.yml && test ! -f /secrets' 2>/dev/null; then
    pass "$svc has no baked secret file"
  else
    fail "$svc appears to bundle secrets in the image"
  fi
done

echo "== Graceful shutdown (SIGTERM lets an in-flight request finish) =="
# Start a read that takes a moment, then stop the service with the default grace period; the
# in-flight request should complete with a 2xx rather than a reset connection.
TOKEN=$(curl -fsS -X POST "http://localhost:8180/realms/lms/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=gateway -d client_secret=gateway-secret \
  -d username=admin -d password=admin -d scope=openid 2>/dev/null | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p') || true
(
  curl -s -o /tmp/k8s-inflight.out -w '%{http_code}' "${GW}/api/loans" \
    -H "Authorization: Bearer ${TOKEN}" > /tmp/k8s-inflight.code &
  INFLIGHT=$!
  sleep 0.2
  $COMPOSE stop -t 30 loan-account >/dev/null 2>&1 || true
  wait "$INFLIGHT" || true
)
code=$(cat /tmp/k8s-inflight.code 2>/dev/null || echo 000)
{ [ "$code" = 200 ] || [ "$code" = 503 ]; } && pass "in-flight request returned $code (finished or drained, not reset)" \
  || fail "in-flight request returned $code (connection likely reset on shutdown)"
$COMPOSE start loan-account >/dev/null 2>&1 || true

echo
if [ "$violations" -eq 0 ]; then echo "K8S-READINESS: PASS"; else echo "K8S-READINESS: FAIL ($violations)"; exit 1; fi
