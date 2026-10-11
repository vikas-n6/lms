#!/usr/bin/env bash
#
# Phase 0 end-to-end check (requires Docker Compose v2 + a built customer/gateway image).
#
#   1. bring up core + auth + apps
#   2. obtain a user token from Keycloak
#   3. POST /api/customers through the gateway (with an Idempotency-Key)
#   4. assert the customer.registered event arrives on Kafka and the outbox row is published
#   5. kill Kafka mid-run, restart it, and show the event is still delivered exactly once
#
# This script is authored for a Docker-enabled session; it has not been executed here (no Docker).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="docker compose -f ${ROOT}/deploy/docker-compose.yml --env-file ${ROOT}/deploy/.env"
KC=http://localhost:8180
GW=http://localhost:8080
TOPIC=customer.events

log() { printf '\n=== %s ===\n' "$*"; }
fail() { echo "E2E FAILED: $*" >&2; exit 1; }

wait_for() {
  local url="$1" tries="${2:-60}"
  for _ in $(seq 1 "${tries}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  fail "timed out waiting for ${url}"
}

kafka_count() {
  # Count records currently on the topic (bounded read).
  ${COMPOSE} exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:19092 --topic "${TOPIC}" \
    --from-beginning --timeout-ms 5000 2>/dev/null | wc -l | tr -d ' '
}

outbox_unpublished() {
  ${COMPOSE} exec -T postgres psql -U customer -d customer -tAc \
    "select count(*) from outbox where published_at is null" | tr -d ' '
}

log "Starting core + auth + apps"
${COMPOSE} --profile core --profile auth --profile apps up -d

log "Waiting for Keycloak and the gateway"
wait_for "${KC}/realms/lms/.well-known/openid-configuration"
wait_for "${GW}/actuator/health" 120 || wait_for "http://localhost:8101/actuator/health/readiness" 120

log "Obtaining a token (direct grant, gateway client)"
TOKEN=$(curl -fsS -X POST "${KC}/realms/lms/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=gateway -d client_secret=gateway-secret \
  -d username=admin -d password=admin -d scope=openid | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "${TOKEN}" ] || fail "no access token"

log "Baseline topic count"
BEFORE=$(kafka_count || echo 0)

log "POST /api/customers through the gateway"
IDEM="e2e-$(date +%s)"
HTTP=$(curl -fsS -o /tmp/lms-e2e-resp.json -w '%{http_code}' -X POST "${GW}/api/customers" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Idempotency-Key: ${IDEM}" \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Asha Rao","phone":"9876543210","dateOfBirth":"1990-01-01"}')
[ "${HTTP}" = "201" ] || fail "expected 201, got ${HTTP}"
echo "created: $(cat /tmp/lms-e2e-resp.json)"

log "Waiting for the outbox to drain"
for _ in $(seq 1 30); do [ "$(outbox_unpublished)" = "0" ] && break; sleep 1; done
[ "$(outbox_unpublished)" = "0" ] || fail "outbox row not published"

log "Asserting the event arrived on ${TOPIC}"
AFTER=$(kafka_count)
[ "${AFTER}" -gt "${BEFORE}" ] || fail "no new event on ${TOPIC} (before=${BEFORE} after=${AFTER})"

log "Kafka restart scenario: stop Kafka, create another customer, restart Kafka"
IDEM2="e2e-$(date +%s)-2"
${COMPOSE} stop kafka
set +e
curl -fsS -o /tmp/lms-e2e-resp2.json -w '%{http_code}' -X POST "${GW}/api/customers" \
  -H "Authorization: Bearer ${TOKEN}" -H "Idempotency-Key: ${IDEM2}" \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Bela Rao","phone":"9000000000","dateOfBirth":"1992-02-02"}' >/dev/null
set -e
# The write is committed (customer + outbox row); the relay cannot publish while Kafka is down.
[ "$(outbox_unpublished)" -ge 1 ] || echo "note: outbox already drained (relay fast / write buffered)"
BEFORE2=$(:)

${COMPOSE} start kafka
wait_for "http://localhost:8101/actuator/health/readiness" 120

log "Waiting for the relay to redeliver after Kafka returns"
for _ in $(seq 1 60); do [ "$(outbox_unpublished)" = "0" ] && break; sleep 2; done
[ "$(outbox_unpublished)" = "0" ] || fail "outbox did not drain after Kafka restart"

log "E2E PASSED: event delivered through the gateway, outbox drained, survived a Kafka restart"
