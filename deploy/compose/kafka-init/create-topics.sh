#!/usr/bin/env bash
# Create every topic in the catalogue (docs/architecture.md) with its retry + DLT variants.
# Idempotent: --if-not-exists. Run as a one-shot init container.
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka:19092}"
PARTITIONS="${PARTITIONS:-6}"
REPLICATION="${REPLICATION:-1}"
K="/opt/kafka/bin/kafka-topics.sh"

# Base business topics (keyed per aggregate).
BASE_TOPICS=(
  customer.events
  application.events
  decision.events
  loan.events
  disbursement.events
  repayment.events
  collections.events
  ledger.events
  notification.events
)

create() {
  local topic="$1"
  "${K}" --bootstrap-server "${BOOTSTRAP}" --create --if-not-exists \
    --topic "${topic}" --partitions "${PARTITIONS}" --replication-factor "${REPLICATION}"
  echo "ensured topic: ${topic}"
}

for base in "${BASE_TOPICS[@]}"; do
  create "${base}"
  create "${base}.retry.5s"
  create "${base}.retry.1m"
  create "${base}.retry.10m"
  create "${base}.dlt"
done

echo "All topics ensured."
