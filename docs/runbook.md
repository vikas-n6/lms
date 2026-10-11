# Runbook

How to start, stop, reset and debug the LMS locally. Phase 0 scope.

## Start / stop / reset

```bash
cp deploy/.env.example deploy/.env     # once
make up-core      # infra only (Postgres, Kafka, Schema Registry, Redis, Kafka UI)
make up-auth      # + Keycloak
make up-full      # everything
make down         # stop containers (keep data)
make reset        # stop + delete volumes (fresh databases/topics)
```

Build artifacts and images:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
make build        # ./mvnw clean verify
make images       # Jib builds lms/customer, lms/gateway images
```

## Where to look when things fail

| Symptom | Where to look |
| --- | --- |
| A service won't start | `docker compose -f deploy/docker-compose.yml logs <svc>`; its `/actuator/health` |
| Readiness never turns UP | DB reachable? migrations applied? `logs customer` for Flyway errors |
| Event not delivered | Kafka UI (http://localhost:8089): topic `customer.events`; the `outbox` table (`published_at`) |
| Schema rejected | `scripts/check-schema-compat.sh`; Schema Registry at :8081 (`/subjects`) |
| 401 from the gateway | token expired / wrong issuer; check `OIDC_ISSUER_URI` and Keycloak realm `lms` |
| 403 from the gateway | user lacks `ADMIN`/`ANALYST` realm role (see the realm users) |
| 429 from the gateway | rate limit hit (100/min per client in Phase 0) |
| Metrics/traces missing | OTel Collector logs; Grafana datasources (Prometheus/Loki/Tempo) |

Useful endpoints: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`.

## Inspecting data

```bash
# Outbox state for the customer service
docker compose -f deploy/docker-compose.yml exec postgres \
  psql -U customer -d customer -c "select event_type, published_at from outbox order by created_at desc limit 10;"

# Tail a topic
docker compose -f deploy/docker-compose.yml exec kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 \
  --topic customer.events --from-beginning --timeout-ms 5000
```

## Common gotchas

- **JDK 25 required.** System Maven may default to another JDK; always set `JAVA_HOME` to 25. The
  `.mvn/jvm.config` adds the `jdk.compiler` exports google-java-format needs (ADR 0002).
- **Preview features** are compiled only in `lms-concurrency`; its container/test JVMs pass
  `--enable-preview`. Do not add the flag elsewhere.
- **Avro trusted packages.** Services set
  `-Dorg.apache.avro.SERIALIZABLE_PACKAGES=com.lms.contracts` (ADR 0004) via `JAVA_TOOL_OPTIONS`;
  without it Avro deserialization of contract classes fails.
- **No Docker here.** This environment has no container runtime, so ITs/compose/e2e are deferred
  (`docs/backlog.md`). Everything else builds green with `./mvnw clean verify`.
```
