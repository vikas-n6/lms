#!/usr/bin/env bash
#
# Scaffold a new service by copying the reference `customer` service, renaming packages and
# registering the module. Produces a compiling hexagonal skeleton to extend.
#
# Usage: scripts/new-service.sh <name> <port>
#   e.g. scripts/new-service.sh origination 8102
#
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <name> <port>" >&2
  exit 1
fi

NAME="$1"            # e.g. origination
PORT="$2"            # e.g. 8102
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${ROOT}/services/customer"
DST="${ROOT}/services/${NAME}"

# Capitalised name for class prefixes (origination -> Origination).
CAP="$(printf '%s' "${NAME:0:1}" | tr '[:lower:]' '[:upper:]')${NAME:1}"

if [[ -e "${DST}" ]]; then
  echo "error: ${DST} already exists" >&2
  exit 1
fi

echo "Scaffolding services/${NAME} (port ${PORT}) from the customer reference ..."
cp -R "${SRC}" "${DST}"
rm -rf "${DST}/target"

# Move the java/resource packages from .../customer/... to .../<name>/...
find "${DST}" -type d -path '*/com/lms/customer' | while read -r dir; do
  mv "${dir}" "${dir%/customer}/${NAME}"
done

# Rename class prefixes and package/identifier tokens inside files.
#   Customer -> Cap  (class names, e.g. CustomerApplication)
#   com.lms.customer -> com.lms.<name>
#   customer.events / schema etc. are left for manual review.
find "${DST}" -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.sql' \) -print0 \
  | while IFS= read -r -d '' f; do
      sed -i.bak \
        -e "s/com\.lms\.customer/com.lms.${NAME}/g" \
        -e "s/Customer/${CAP}/g" \
        -e "s/customer/${NAME}/g" \
        "${f}"
      rm -f "${f}.bak"
    done

# Set the port in application.yml.
sed -i.bak "s/SERVER_PORT:[0-9]*/SERVER_PORT:${PORT}/" "${DST}/src/main/resources/application.yml" \
  && rm -f "${DST}/src/main/resources/application.yml.bak"

# Register the module in the root pom (before </modules>), if not already present.
if ! grep -q "<module>services/${NAME}</module>" "${ROOT}/pom.xml"; then
  sed -i.bak "s|\(\s*\)</modules>|\1  <module>services/${NAME}</module>\n\1</modules>|" "${ROOT}/pom.xml"
  rm -f "${ROOT}/pom.xml.bak"
fi

cat <<EOF

Done. services/${NAME} created and registered.

Next:
  1. Review generated files (event topic names, SQL table names, DTOs) — the blind
     'customer'->'${NAME}' rename needs a human pass.
  2. Add a compose service block to deploy/docker-compose.yml:

     ${NAME}:
       image: lms/${NAME}:0.1.0-SNAPSHOT
       environment:
         SERVER_PORT: ${PORT}
         DB_URL: jdbc:postgresql://postgres:5432/${NAME}
         SCHEMA_REGISTRY_URL: http://schema-registry:8081
         KAFKA_BOOTSTRAP: kafka:9092
         OIDC_ISSUER_URI: http://keycloak:8080/realms/lms
         JAVA_TOOL_OPTIONS: "-Dorg.apache.avro.SERIALIZABLE_PACKAGES=com.lms.contracts"
       ports: ["${PORT}:${PORT}"]

  3. Add a '${NAME}' database + role to deploy/postgres-init/.
  4. Build: JAVA_HOME=<jdk25> ./mvnw -pl services/${NAME} -am verify
EOF
