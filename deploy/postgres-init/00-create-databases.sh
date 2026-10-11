#!/usr/bin/env bash
# One database + one least-privilege role per service (docs/architecture.md). Roles can only
# access their own database. Dev passwords equal the service name; override in real deployments.
set -euo pipefail

SERVICES=(customer origination decision loan_account disbursement repayment collections ledger notification reporting)

for svc in "${SERVICES[@]}"; do
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname postgres <<-SQL
    DO \$\$
    BEGIN
      IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${svc}') THEN
        CREATE ROLE "${svc}" LOGIN PASSWORD '${svc}';
      END IF;
    END
    \$\$;
    SELECT 'CREATE DATABASE "${svc}" OWNER "${svc}"'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${svc}')\gexec
    REVOKE ALL ON DATABASE "${svc}" FROM PUBLIC;
    GRANT ALL PRIVILEGES ON DATABASE "${svc}" TO "${svc}";
SQL
  echo "ensured database+role: ${svc}"
done
