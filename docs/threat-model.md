# Threat model (Phase 5 security pass)

Short STRIDE per trust boundary. This is a personal learning project: regulations and every external
party are mocked (CLAUDE.md), so the model covers the boundaries that exist in the design, with the
mitigations already built. "Deferred" items are tracked in `docs/backlog.md`.

## Assets

- Customer PII (phone, PAN-like id, bank account, DOB) — encrypted at rest, masked in logs.
- Money truth: loan balances, schedules, allocations and the double-entry ledger.
- Auth material: Keycloak tokens, service client credentials, the callback HMAC secret, column keys.
- Event stream integrity (Kafka) and the audit trail.

## Trust boundaries

1. Browser ↔ gateway (edge). 2. Gateway ↔ services (internal). 3. Service ↔ its database.
4. Service ↔ Kafka. 5. Provider simulators ↔ services (async callbacks). 6. Operator ↔ admin APIs.

## STRIDE by boundary

| Boundary | Threat (STRIDE) | Mitigation (built) |
| --- | --- | --- |
| Browser ↔ gateway | **S**poofing | Keycloak OIDC; gateway validates JWT, maps realm roles; stateless |
| | **T**ampering | TLS in deployment; `Idempotency-Key` + stored response stop replayed writes |
| | **R**epudiation | Audit events for admin actions (ADR 0014), `traceId` on every log line |
| | **I**nfo disclosure | Role rules (reporting/audit are admin+analyst, not customer); PII masked in logs |
| | **D**oS | Redis sliding-window rate limiting; limiter fails open with a metric when Redis is down |
| | **E**oP | Roles enforced at the edge; per-record ownership in services (partly deferred) |
| Gateway ↔ services | **S**poofing | Service-to-service REST only via the gateway or to simulators; client-credentials tokens |
| | **T**ampering | Internal network; optional mTLS profile (below) for defence in depth |
| Service ↔ database | **I**nfo disclosure | One DB + least-privilege role per service; no cross-service DB access (ArchUnit) |
| | | PII columns AES-GCM encrypted (`FieldCipher`/`KeyRing`); blind index for equality lookups |
| | **T**ampering | Ledger/allocation/state-history are append-only (triggers + withheld UPDATE/DELETE grants) |
| Service ↔ Kafka | **T**ampering/repudiation | Publish only via the transactional outbox; consume with an inbox (exactly-once effect) |
| | **I**nfo disclosure | Events carry ids and amounts, never plaintext PII (schema review; no-PII e2e log check) |
| Simulator ↔ services | **S**poofing | Callbacks bypass JWT but require a valid HMAC signature over timestamp+raw body (ADR 0009) |
| | **T**ampering/replay | Constant-time compare; skew window; dedup on the provider reference (Redis + Postgres) |
| Operator ↔ admin APIs | **R**epudiation | Every admin action is audited with who/what/before/after/correlation (ADR 0014) |
| | **E**oP | `/api/admin/**`, write-off, simulator control, DLT replay are admin-only; two-person write-off |

## Optional mTLS profile (service-to-service)

Beyond client-credentials JWTs, an optional mutual-TLS profile hardens the internal boundary so a
service accepts connections only from peers presenting a trusted client certificate. It is a
deployment profile, not new code:

- Issue a per-service key pair and a shared internal CA (dev: a generated CA mounted into each
  container; real: the cluster's mesh/issuer).
- Each service's resource-server listener requires client auth; Spring Boot SSL bundles
  (`server.ssl.bundle`, `server.ssl.client-auth=need`) point at the mounted key/trust stores, all via
  environment — no secrets in Git.
- The gateway presents its client cert to upstreams; simulators stay on the signed-callback path.
- Enabled with a `mtls` compose profile that mounts the stores and sets the SSL env vars; off by
  default so the local stack stays simple. Certificate issuance/rotation is a backlog item.

## Column-encryption key rotation

Keys are versioned in a `KeyRing` (`lms-security`): tokens are stored as `<keyId>:<ciphertext>`, new
writes use the current key, and old keys stay in the ring for reads. Rotation re-encrypts row by row
with `KeyRing.rotate`. The operator procedure and the dry-run on a data copy are in
`docs/runbook.md` ("Column-encryption key rotation").

## Verified in CI

`.github/workflows/security.yml` runs three scans. The **hard fail-gates** are the gitleaks secret
scan and the Trivy filesystem **dependency scan** (declared deps + lockfiles, fail on HIGH/CRITICAL,
`--ignore-unfixed`) — a deliberately introduced vulnerable dependency fails the dependency scan,
which is the acceptance proof. The Trivy **image scan** is **report-only**: the image bundles
framework libraries at their Spring-Boot-BOM-managed versions (e.g. tomcat-embed-core, jackson) that
periodically carry CVEs fixable only by a coordinated Boot BOM upgrade (tracked in `docs/backlog.md`),
so it surfaces findings for visibility without blocking on upstream-unpatched framework CVEs. Two
findings are triaged in `.trivyignore` with justifications (an optional, unshipped lz4 fork; and
guava, pinned for Temporal convergence per ADR 0008). The planted-vuln/planted-secret acceptance
demonstration is run once in the CI session.
