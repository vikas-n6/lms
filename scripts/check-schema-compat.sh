#!/usr/bin/env bash
#
# Check every Avro schema in contracts/ for BACKWARD compatibility against a running
# Schema Registry (docs/architecture.md "Kafka rules": Schema Registry in BACKWARD mode).
#
# Requires a running Schema Registry (compose profile `core`). Deferred in environments without
# Docker (docs/backlog.md). Exits non-zero on the first incompatible schema so CI can fail.
#
# Usage: scripts/check-schema-compat.sh [schema-registry-url]
set -euo pipefail

SR_URL="${1:-${SCHEMA_REGISTRY_URL:-http://localhost:8081}}"
AVRO_DIR="$(cd "$(dirname "$0")/.." && pwd)/contracts/src/main/avro"

if ! command -v jq >/dev/null 2>&1; then
  echo "error: jq is required" >&2
  exit 2
fi

echo "Checking Avro schemas in ${AVRO_DIR} against ${SR_URL} (BACKWARD) ..."
rc=0
for schema in "${AVRO_DIR}"/*.avsc; do
  name="$(basename "${schema}" .avsc)"
  subject="${name}-value"
  # Wrap the schema file as a Schema Registry request body.
  body="$(jq -c '{schema: (. | tojson)}' "${schema}")"

  # If the subject has no versions yet, compatibility is trivially satisfied.
  if ! curl -fsS "${SR_URL}/subjects/${subject}/versions" >/dev/null 2>&1; then
    echo "  ${subject}: new subject (no existing version) -> OK"
    continue
  fi

  result="$(curl -fsS -X POST \
    -H 'Content-Type: application/vnd.schemaregistry.v1+json' \
    --data "${body}" \
    "${SR_URL}/compatibility/subjects/${subject}/versions/latest" | jq -r '.is_compatible')"

  if [[ "${result}" == "true" ]]; then
    echo "  ${subject}: compatible -> OK"
  else
    echo "  ${subject}: INCOMPATIBLE" >&2
    rc=1
  fi
done

exit "${rc}"
