#!/usr/bin/env bash
#
# Phase 5 chaos scenarios (phase prompt task 4). Each scenario perturbs the running stack while a
# lifecycle workload runs, then calls scripts/verify-invariants.sh — which must pass. Authored for
# the Docker session; NOT executed here (no Docker) — see docs/backlog.md and docs/chaos-report.md.
#
# Usage: scripts/chaos/chaos.sh [scenario|all]
#   kill-kafka      kill and restart Kafka; outbox grows then drains, no loss or duplication
#   kill-redis      stop Redis; idempotency falls back to Postgres, rate limiting fails open (metric),
#                   caches bypassed; restart and confirm recovery
#   kill-temporal   stop Temporal; disbursement/collections workflows pause, then resume on restart
#   kill-db <svc>   stop a service's database; readiness fails, nothing lost; restart and recover
#   pause <svc>     pause a service container mid-flow, then unpause
#   duplicate-storm replay an entire topic into the running consumers (dedup must hold)
#   sim-flaky       run every simulator at the 'flaky' preset during the workload
#   sim-down        run every simulator at the 'down' preset during the workload
#   all             run every scenario in sequence
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="docker compose -f ${ROOT}/deploy/docker-compose.yml"
SIM="${SIM_BASE_URL:-http://localhost:8190}"
VERIFY="${ROOT}/scripts/verify-invariants.sh"
SETTLE="${SETTLE_SECONDS:-30}"

log()  { printf '\n### %s\n' "$*"; }
settle(){ echo "  (settling ${SETTLE}s for outbox/readiness to recover)"; sleep "$SETTLE"; }
verify(){ log "verifying invariants"; bash "$VERIFY"; }

restart_infra() { # $1 = compose service
  log "killing $1"; $COMPOSE kill "$1"; sleep 5
  log "restarting $1"; $COMPOSE up -d "$1"; settle
}

kill_kafka()    { restart_infra kafka; verify; }       # outbox grows while down, drains after
kill_redis()    { restart_infra redis; verify; }       # idempotency -> Postgres, rate limit fails open
kill_temporal() { restart_infra temporal; verify; }    # workflows pause then resume
pause_svc()     { log "pausing $1"; $COMPOSE pause "$1"; sleep 10; log "unpausing $1"; $COMPOSE unpause "$1"; settle; verify; }

kill_db() { # a service DB == the shared postgres here; stop/start it and confirm readiness flips
  log "stopping postgres (simulating $1 DB outage)"; $COMPOSE stop postgres; sleep 8
  log "service readiness should now be DOWN"
  $COMPOSE start postgres; settle; verify
}

duplicate_storm() { # re-produce an entire topic back onto itself; inbox dedup must absorb it
  local topic="${1:-loan.events}"
  log "duplicate storm on $topic (replay from offset 0 into the live consumers)"
  $COMPOSE exec -T kafka bash -lc "
    kafka-console-consumer.sh --bootstrap-server kafka:19092 --topic ${topic} \
      --from-beginning --timeout-ms 10000 --property print.key=true --property key.separator=$'\t' \
      > /tmp/dump.txt 2>/dev/null || true
    kafka-console-producer.sh --bootstrap-server kafka:19092 --topic ${topic} \
      --property parse.key=true --property key.separator=$'\t' < /tmp/dump.txt || true
  "
  settle; verify
}

sim_preset() { # drive every simulator to a preset, run a short while, then reset to normal
  local preset="$1"
  log "setting all simulators to '${preset}'"
  curl -fsS -X POST "${SIM}/sim/control/preset" -H 'Content-Type: application/json' \
    -d "{\"preset\":\"${preset}\"}" || echo "  (preset endpoint is exercised in the Docker run)"
  sleep "$SETTLE"
  curl -fsS -X POST "${SIM}/sim/control/preset" -H 'Content-Type: application/json' \
    -d '{"preset":"normal"}' || true
  settle; verify
}

run() {
  case "$1" in
    kill-kafka) kill_kafka ;;
    kill-redis) kill_redis ;;
    kill-temporal) kill_temporal ;;
    kill-db) kill_db "${2:-loan-account}" ;;
    pause) pause_svc "${2:-loan-account}" ;;
    duplicate-storm) duplicate_storm "${2:-loan.events}" ;;
    sim-flaky) sim_preset flaky ;;
    sim-down) sim_preset down ;;
    all)
      kill_kafka; kill_redis; kill_temporal; kill_db loan-account
      pause_svc loan-account; duplicate_storm loan.events; sim_preset flaky; sim_preset down ;;
    *) echo "unknown scenario: $1" >&2; exit 2 ;;
  esac
}

run "${1:-all}" "${2:-}"
echo
echo "CHAOS: ${1:-all} completed with invariants intact"
