# Convenience wrapper around docker compose and the Maven build.
# Requires Docker Compose v2. The compose stack lives in deploy/.

COMPOSE := docker compose -f deploy/docker-compose.yml --env-file deploy/.env
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 25 2>/dev/null)
MVN := JAVA_HOME=$(JAVA_HOME) ./mvnw

.PHONY: help build images up-core up-auth up-workflow up-observability up-apps up-full down reset e2e

help:
	@echo "Targets:"
	@echo "  build            ./mvnw clean verify (JDK 25)"
	@echo "  images           build service container images via Jib"
	@echo "  up-core          start Postgres, Kafka, Schema Registry, Redis, Kafka UI (+ topics)"
	@echo "  up-auth          start Keycloak (lms realm)"
	@echo "  up-workflow      start Temporal dev server + UI"
	@echo "  up-observability start OTel Collector, Prometheus, Grafana, Loki, Tempo"
	@echo "  up-apps          start core + the services"
	@echo "  up-full          start everything"
	@echo "  down             stop and remove containers"
	@echo "  reset            down + delete volumes (fresh databases)"
	@echo "  e2e              run scripts/e2e/phase0.sh against a running stack"

build:
	$(MVN) clean verify

images:
	$(MVN) -pl services/customer -am compile jib:dockerBuild

up-core:
	$(COMPOSE) --profile core up -d
up-auth:
	$(COMPOSE) --profile auth up -d
up-workflow:
	$(COMPOSE) --profile workflow up -d
up-observability:
	$(COMPOSE) --profile observability up -d
up-apps:
	$(COMPOSE) --profile apps up -d
up-full:
	$(COMPOSE) --profile full up -d

down:
	$(COMPOSE) --profile full down

reset:
	$(COMPOSE) --profile full down -v

e2e:
	./scripts/e2e/phase0.sh
